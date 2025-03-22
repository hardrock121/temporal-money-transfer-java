package io.temporal.samples.moneytransfer;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import java.time.Duration;

public class WorkflowConfig {
    public static final int DEFAULT_APPROVAL_TIMEOUT_SECONDS = 30;
    public static final int DEFAULT_ACTIVITY_TIMEOUT_SECONDS = 5;
    public static final int DEFAULT_ACTIVITY_RETRY_DELAY_SECONDS = 2;
    public static final int DEFAULT_WORKFLOW_DELAY_SECONDS = 6;

    public static ActivityOptions getActivityOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(DEFAULT_ACTIVITY_TIMEOUT_SECONDS))
                .setRetryOptions(
                        RetryOptions.newBuilder()
                                .setDoNotRetry(
                                        AccountTransferActivitiesImpl.InvalidAccountException.class.getName())
                                .build())
                .build();
    }
} 