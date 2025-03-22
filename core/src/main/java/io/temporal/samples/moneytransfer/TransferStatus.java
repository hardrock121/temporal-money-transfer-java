package io.temporal.samples.moneytransfer;

import io.temporal.samples.moneytransfer.dataclasses.ChargeResponseObj;

public class TransferStatus {
    private int progressPercentage;
    private TransferState state;
    private ChargeResponseObj chargeResult;
    private int approvalTimeout;

    public TransferStatus() {
        this.progressPercentage = 10;
        this.state = TransferState.STARTING;
        this.chargeResult = new ChargeResponseObj("");
        this.approvalTimeout = WorkflowConfig.DEFAULT_APPROVAL_TIMEOUT_SECONDS;
    }

    public void updateProgress(int percentage) {
        this.progressPercentage = percentage;
    }

    public void updateState(TransferState newState) {
        this.state = newState;
    }

    public void setChargeResult(ChargeResponseObj result) {
        this.chargeResult = result;
    }

    public int getProgressPercentage() {
        return progressPercentage;
    }

    public TransferState getState() {
        return state;
    }

    public ChargeResponseObj getChargeResult() {
        return chargeResult;
    }

    public int getApprovalTimeout() {
        return approvalTimeout;
    }
} 