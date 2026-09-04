package com.fashion.mapper;

import com.fashion.entity.ProductProjectionTask;
import com.fashion.entity.ProductCatalogRevision;
import com.fashion.entity.ProductProjectionReconcileRun;
import com.fashion.entity.ProductProjectionStatusSummary;
import com.fashion.entity.ProductProjectionTaskView;
import com.fashion.product.ProductCatalogAuthority;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProductCatalogMapper extends ProductCatalogAuthority {

    @Override
    long readListVersion();

    @Override
    ProductCatalogRevision readRevision(@Param("productId") long productId);

    Long lockListVersion();

    int advanceListVersion(@Param("expectedVersion") long expectedVersion);

    int upsertRevision(@Param("productId") long productId,
                       @Param("itemVersion") long itemVersion,
                       @Param("itemState") String itemState);

    int insertProjectionTask(ProductProjectionTask task);

    int insertProjectionTaskIfAbsent(ProductProjectionTask task);

    ProductCatalogRevision readRevisionForUpdate(@Param("productId") long productId);

    int terminalizeExhausted(@Param("target") String target,
                             @Param("maxAttempts") int maxAttempts);

    ProductProjectionTask lockNextClaimable(@Param("target") String target,
                                            @Param("maxAttempts") int maxAttempts);

    int claimEsRevisionLease(@Param("productId") long productId,
                             @Param("token") String token,
                             @Param("lockedUntil") LocalDateTime lockedUntil);

    int releaseEsRevisionLease(@Param("productId") long productId,
                               @Param("token") String token);

    int markTaskProcessing(@Param("id") long id,
                           @Param("token") String token,
                           @Param("lockedUntil") LocalDateTime lockedUntil,
                           @Param("maxAttempts") int maxAttempts);

    ProductProjectionTask readProjectionTask(@Param("id") long id);

    int ownsProjectionDeliveryLease(@Param("id") long id,
                                    @Param("token") String token,
                                    @Param("requiredUntil") LocalDateTime requiredUntil);

    int ownsCurrentProjectionDelivery(@Param("id") long id,
                                      @Param("token") String token,
                                      @Param("requiredUntil") LocalDateTime requiredUntil);

    int completeProjectionTask(@Param("id") long id,
                               @Param("token") String token,
                               @Param("status") String status,
                               @Param("nextRetryAt") LocalDateTime nextRetryAt,
                               @Param("lastErrorSummary") String lastErrorSummary);

    int insertReconcileRun(ProductProjectionReconcileRun run);

    ProductProjectionReconcileRun readActiveReconcileRun();

    ProductProjectionReconcileRun readLatestReconcileRun();

    List<ProductProjectionTask> listCurrentEsTasksAfter(@Param("afterProductId") long afterProductId,
                                                        @Param("limit") int limit);

    ProductProjectionTask readCurrentEsTask(@Param("productId") long productId);

    int reopenProjectionTaskForRepair(@Param("id") long id,
                                      @Param("maxRepairs") int maxRepairs);

    int terminalizeExhaustedReconcileRuns(@Param("maxAttempts") int maxAttempts);

    int claimReconcileRun(@Param("token") String token,
                          @Param("lockedUntil") LocalDateTime lockedUntil,
                          @Param("maxAttempts") int maxAttempts);

    ProductProjectionReconcileRun readClaimedReconcileRun(@Param("token") String token);

    int saveClaimedReconcileRun(ProductProjectionReconcileRun run);

    int failClaimedReconcileRun(@Param("id") long id,
                                @Param("token") String token,
                                @Param("status") String status,
                                @Param("nextRetryAt") LocalDateTime nextRetryAt,
                                @Param("lastErrorSummary") String lastErrorSummary);

    List<ProductProjectionStatusSummary> summarizeProjectionTasks();

    List<ProductProjectionTaskView> listProjectionTaskViews(@Param("target") String target,
                                                            @Param("status") String status,
                                                            @Param("limit") int limit);

    int replayTerminalProjectionTask(@Param("id") long id);
}
