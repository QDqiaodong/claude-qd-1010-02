package com.museum.collection.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.museum.collection.common.BizException;
import com.museum.collection.common.Dict;
import com.museum.collection.entity.Gallery;
import com.museum.collection.entity.Tour;
import com.museum.collection.repository.GalleryRepository;
import com.museum.collection.repository.TourRepository;

/**
 * 导览业务。
 *
 * <p>核心规则：① 新增导览受展厅容量约束——该展厅「待开始 + 进行中」的场次不得超过其 capacity，
 * 超出则拒绝（中文 BizException）；「已完成」场次不再占用容量，但记录仍保留在导览历史与
 * 按状态筛选结果中。② 状态只能逐级推进：待开始 → 进行中 → 已完成，终态不可重复结束。</p>
 *
 * <p>并发口径：所有写操作都遵循「先锁展厅行、再锁导览行」的同一加锁顺序。两名工作人员同时
 * 排期同一展厅时，后到者阻塞在展厅行锁上，拿到锁后在锁内重新计数，看到的是对方已提交的
 * 最新容量，不会超额；同时结束（或一人结束、一人排期）同理，结束动作释放的名额对随后的
 * 排期立即可见，且重复结束的第二次请求得到明确失败原因、状态不变，不会出现重复释放。</p>
 */
@Service
public class TourService {

    private final TourRepository tourRepository;
    private final GalleryRepository galleryRepository;
    private final TransactionTemplate tx;

    public TourService(TourRepository tourRepository,
                       GalleryRepository galleryRepository,
                       PlatformTransactionManager transactionManager) {
        this.tourRepository = tourRepository;
        this.galleryRepository = galleryRepository;
        this.tx = new TransactionTemplate(transactionManager);
    }

    // ---------------- 查询 ----------------

    /**
     * 列表查询：可按展厅与状态组合筛选。已完成记录永远在全量列表与「已完成」筛选结果中，
     * 不因释放容量而被隐藏。
     */
    public List<Tour> list(Long galleryId, String status) {
        boolean hasStatus = status != null && !status.isBlank();
        if (galleryId != null) {
            List<Tour> base = tourRepository.findByGalleryId(galleryId);
            return hasStatus
                    ? base.stream().filter(t -> status.equals(t.getStatus())).toList()
                    : base;
        }
        if (hasStatus) {
            return tourRepository.findByStatus(status);
        }
        return tourRepository.findAll(Sort.by(Sort.Direction.DESC, "id"));
    }

    public Tour get(Long id) {
        return tourRepository.findById(id)
                .orElseThrow(() -> new BizException("导览不存在：" + id));
    }

    private String nextTourNo() {
        int year = LocalDateTime.now().getYear();
        int maxSeq = 0;
        Pattern p = Pattern.compile("^TO-" + year + "-(\\d+)$");
        for (Tour t : tourRepository.findAll()) {
            if (t.getTourNo() == null) {
                continue;
            }
            Matcher m = p.matcher(t.getTourNo());
            if (m.find()) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(m.group(1)));
            }
        }
        return String.format("TO-%d-%03d", year, maxSeq + 1);
    }

    // ---------------- 排期 ----------------

    /**
     * 排期导览。整个校验-计数-插入在单个事务内完成，并先锁展厅行：
     * 并发排期同展厅时被行锁串行化，锁内用最新数据计数，不会出现两人都看到“还剩一个名额”。
     *
     * <p>外层不开事务：导览编号按年度序号生成，两名工作人员同时给不同展厅排期可能算出同号，
     * 撞唯一约束时整个短事务回滚并在新事务中取号重试，避免一次偶发冲突直接报错给用户。</p>
     */
    public Tour create(Tour input) {
        if (input.getGalleryCode() == null || input.getGalleryCode().isBlank()) {
            throw new BizException("必须指定所属展厅编号");
        }
        String galleryCode = input.getGalleryCode().trim();
        String customNo = input.getTourNo() != null && !input.getTourNo().isBlank()
                ? input.getTourNo().trim() : null;
        String title = input.getTitle();
        String guideName = input.getGuideName();
        LocalDateTime scheduledAt = input.getScheduledAt();
        String initialStatus = input.getStatus() != null && !input.getStatus().isBlank()
                ? input.getStatus() : "待开始";
        if (!Dict.isValidTourStatus(initialStatus)) {
            throw new BizException("导览状态非法：" + initialStatus);
        }

        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> doCreate(galleryCode, customNo, title, guideName, scheduledAt, initialStatus));
            } catch (DataIntegrityViolationException e) {
                // 仅自动编号在并发下撞 tour_no 唯一约束时重试一次；自定义编号冲突直接报错
                if (customNo == null && attempt == 1) {
                    continue;
                }
                throw new BizException("导览编号已存在，请重新提交排期");
            }
        }
    }

    private Tour doCreate(String galleryCode, String customNo, String title, String guideName,
                          LocalDateTime scheduledAt, String initialStatus) {
        // 加锁前只取 id（标量投影，不污染一级缓存）；展厅不存在直接拒绝，原状态/数据不变
        Long galleryId = galleryRepository.findIdByGalleryCode(galleryCode)
                .orElseThrow(() -> new BizException("展厅不存在：" + galleryCode));
        // 先锁展厅行：同一展厅的并发排期 / 结束在这里串行，保证锁内计数是最新的
        Gallery locked = galleryRepository.findByIdForUpdate(galleryId)
                .orElseThrow(() -> new BizException("展厅不存在：" + galleryCode));
        if (customNo != null) {
            tourRepository.findByTourNo(customNo).ifPresent(t -> {
                throw new BizException("导览编号已存在：" + customNo);
            });
        }
        // 当前读计数：拿到展厅锁后，以加锁读锁定该展厅全部占用中的导览行并取最新版本，
        // 别人刚结束（已完成）的场次不会计入，别人刚插入的占用场次一定会计入
        List<Tour> occupying = tourRepository.findOccupyingForUpdate(locked.getId(), Dict.TOUR_OCCUPYING);
        long used = occupying.size();
        // 新排期自身若以占用中状态（待开始 / 进行中）建档也要占一个名额；直接登档为已完成的历史场不占
        boolean newOccupies = Dict.TOUR_OCCUPYING.contains(initialStatus);
        if (used + (newOccupies ? 1 : 0) > locked.getCapacity()) {
            throw new BizException("展厅「" + locked.getName() + "」容量为 " + locked.getCapacity()
                    + "，当前有 " + used + " 场待开始或进行中的导览，容量已满，无法新增排期；"
                    + "已完成的导览不再占用容量，待有场次结束后可再排");
        }
        Tour tour = new Tour();
        tour.setTourNo(customNo != null ? customNo : nextTourNo());
        tour.setGalleryId(locked.getId());
        tour.setGalleryCode(locked.getGalleryCode());
        tour.setGalleryName(locked.getName());
        tour.setTitle(title);
        tour.setGuideName(guideName);
        tour.setScheduledAt(scheduledAt);
        tour.setStatus(initialStatus);
        tour.setCreatedAt(LocalDateTime.now());
        return tourRepository.saveAndFlush(tour);
    }

    // ---------------- 状态流转 ----------------

    /** 开始：待开始 → 进行中（不增加占用名额，但同样按「展厅 → 导览」顺序加锁，与排期/结束互斥） */
    @Transactional
    public Tour start(Long id) {
        Tour existing = lockTourInGallery(id);
        if (!Dict.canTransferTour(existing.getStatus(), "进行中")) {
            if ("已完成".equals(existing.getStatus())) {
                throw new BizException("导览「" + existing.getTourNo() + "」已完成，不能再开始；"
                        + "如需安排请重新排期一场新导览");
            }
            if ("进行中".equals(existing.getStatus())) {
                throw new BizException("导览「" + existing.getTourNo() + "」已在进行中，无需重复开始");
            }
            throw new BizException("导览状态不可从「" + existing.getStatus() + "」开始");
        }
        existing.setStatus("进行中");
        return tourRepository.save(existing);
    }

    /**
     * 结束：进行中 → 已完成，完成后该场立即不再计入展厅已用场次。
     * 两人同时结束时，后到者在锁内看到的是「已完成」，得到明确失败原因且状态保持已完成，
     * 不会产生“重复释放”（容量本就按状态实时统计，不存在可被重复扣减的计数器）。
     */
    @Transactional
    public Tour finish(Long id) {
        Tour existing = lockTourInGallery(id);
        if (!Dict.canTransferTour(existing.getStatus(), "已完成")) {
            if ("已完成".equals(existing.getStatus())) {
                throw new BizException("导览「" + existing.getTourNo() + "」已完成，不能重复结束；"
                        + "该场已释放展厅容量，状态保持为已完成");
            }
            if ("待开始".equals(existing.getStatus())) {
                throw new BizException("导览「" + existing.getTourNo() + "」尚未开始，不能结束；"
                        + "请先开始再结束");
            }
            throw new BizException("导览状态不可从「" + existing.getStatus() + "」结束");
        }
        existing.setStatus("已完成");
        return tourRepository.save(existing);
    }

    /** 通用状态跳转：非法目标状态、跳级、终态再流转都会被拒绝且原状态不变 */
    @Transactional
    public Tour transfer(Long id, String target) {
        if (!Dict.isValidTourStatus(target)) {
            throw new BizException("导览状态非法：" + target);
        }
        Tour existing = lockTourInGallery(id);
        if (target.equals(existing.getStatus())) {
            throw new BizException("导览「" + existing.getTourNo() + "」当前已是「" + target + "」，状态未改变");
        }
        if (!Dict.canTransferTour(existing.getStatus(), target)) {
            throw new BizException("导览状态不可从「" + existing.getStatus() + "」跳转到「" + target
                    + "」，只能逐级推进：待开始 → 进行中 → 已完成");
        }
        existing.setStatus(target);
        return tourRepository.save(existing);
    }

    // ---------------- 内部 ----------------

    /**
     * 按统一顺序加锁：先展厅行、再导览行（与排期相同）。
     * 先用投影只读查询拿到所属展厅 id（不把导览装进一级缓存），锁住展厅行后再以加锁读
     * 首次装配导览实体——锁内拿到的一定是最新状态。这样并发的排期、开始、结束彼此互斥，
     * 锁内读到的状态与容量永远一致，也不会循环等锁形成死锁。
     */
    private Tour lockTourInGallery(Long id) {
        TourRepository.TourRef ref = tourRepository.findRefById(id)
                .orElseThrow(() -> new BizException("导览不存在：" + id));
        if (ref.getGalleryId() == null) {
            throw new BizException("导览「" + ref.getTourNo() + "」未绑定展厅，无法流转");
        }
        galleryRepository.findByIdForUpdate(ref.getGalleryId())
                .orElseThrow(() -> new BizException("展厅不存在：" + ref.getGalleryId()));
        return tourRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BizException("导览不存在：" + id));
    }
}
