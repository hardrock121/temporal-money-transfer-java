/*
 *  Copyright (c) 2020 Temporal Technologies, Inc. All Rights Reserved
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.samples.moneytransfer;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.SearchAttributeKey;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.samples.moneytransfer.dataclasses.*;
import io.temporal.samples.moneytransfer.web.ServerInfo;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AccountTransferWorkflowImpl implements AccountTransferWorkflow {

  static final SearchAttributeKey<String> WORKFLOW_STEP = SearchAttributeKey.forKeyword("Step");

  private static final Logger log = LoggerFactory.getLogger(AccountTransferWorkflowImpl.class);

  private final ActivityOptions options = WorkflowConfig.getActivityOptions();
  private final AccountTransferActivities accountTransferActivities =
      Workflow.newActivityStub(AccountTransferActivities.class, options);

  private final TransferStatus status = new TransferStatus();
  private boolean approved = false;

  @Override
  public ResultObj transfer(WorkflowParameterObj params) {
    try {
      initializeTransfer();
      validateAndWaitForApproval(params);
      executeTransfer(params);
      completeTransfer();
      return new ResultObj(status.getChargeResult());
    } catch (Exception e) {
      handleTransferFailure(e, params);
      throw e;
    }
  }

  private void initializeTransfer() {
    status.updateState(TransferState.STARTING);
    status.updateProgress(25);
    Workflow.sleep(Duration.ofSeconds(ServerInfo.getWorkflowSleepDuration()));
    status.updateProgress(50);
    status.updateState(TransferState.RUNNING);
  }

  private void validateAndWaitForApproval(WorkflowParameterObj params) {
    if (!accountTransferActivities.validate(params.getScenario())) {
      log.info("\n\nWaiting on 'approveTransfer' Signal or Update for workflow ID: " +
          Workflow.getInfo().getWorkflowId() + "\n\n");
      status.updateState(TransferState.WAITING);

      boolean receivedSignal = Workflow.await(
          Duration.ofSeconds(status.getApprovalTimeout()),
          () -> approved
      );

      if (!receivedSignal) {
        throw ApplicationFailure.newFailure(
            "Approval not received within " + status.getApprovalTimeout() + " seconds",
            "ApprovalTimeout"
        );
      }
    }
  }

  private void executeTransfer(WorkflowParameterObj params) {
    status.updateProgress(60);
    status.updateState(TransferState.RUNNING);

    if (params.getScenario() == ExecutionScenarioObj.ADVANCED_VISIBILITY) {
      Workflow.upsertTypedSearchAttributes(WORKFLOW_STEP.valueSet("Withdraw"));
      Workflow.sleep(Duration.ofSeconds(5));
    }

    accountTransferActivities.withdraw(params.getAmount(), params.getScenario());
    Workflow.sleep(Duration.ofSeconds(WorkflowConfig.DEFAULT_ACTIVITY_RETRY_DELAY_SECONDS));

    if (params.getScenario() == ExecutionScenarioObj.BUG_IN_WORKFLOW) {
      log.info("\n\nSimulating workflow task failure.\n\n");
      throw new RuntimeException("Workflow Bug!");
    }

    if (params.getScenario() == ExecutionScenarioObj.ADVANCED_VISIBILITY) {
      Workflow.upsertTypedSearchAttributes(WORKFLOW_STEP.valueSet("Deposit"));
    }

    String idempotencyKey = Workflow.randomUUID().toString();
    ChargeResponseObj result = accountTransferActivities.deposit(
        idempotencyKey, params.getAmount(), params.getScenario()
    );
    status.setChargeResult(result);
  }

  private void completeTransfer() {
    status.updateProgress(80);
    Workflow.sleep(Duration.ofSeconds(WorkflowConfig.DEFAULT_WORKFLOW_DELAY_SECONDS));
    status.updateProgress(100);
    status.updateState(TransferState.FINISHED);
  }

  private void handleTransferFailure(Exception e, WorkflowParameterObj params) {
    if (e instanceof ActivityFailure) {
      log.info("\n\nDeposit failed unrecoverably, reverting withdraw\n\n");
      accountTransferActivities.undoWithdraw(params.getAmount());
      String message = ((ApplicationFailure) e.getCause()).getOriginalMessage();
      throw ApplicationFailure.newNonRetryableFailure(message, "DepositFailed");
    }
  }

  @Override
  public StateObj getStateQuery() {
    return new StateObj(
        status.getProgressPercentage(),
        status.getState().getValue(),
        "",
        status.getChargeResult(),
        status.getApprovalTimeout()
    );
  }

  @Override
  public void approveTransfer() {
    log.info("\n\nApprove Signal Received\n\n");
    if (status.getState() == TransferState.WAITING) {
      this.approved = true;
    } else {
      log.info("\n\nSignal not applied: Transfer is not waiting for approval.\n\n");
    }
  }

  @Override
  public String approveTransferUpdate() {
    log.info("\n\nApprove Update Validated: Approving Transfer\n\n");
    this.approved = true;
    return "successfully approved transfer";
  }

  @Override
  public void approveTransferUpdateValidator() {
    log.info("\n\nApprove Update Received: Validating\n\n");
    if (this.approved) {
      throw new IllegalStateException("Validation Failed: Transfer already approved");
    }
    if (status.getState() != TransferState.WAITING) {
      throw new IllegalStateException("Validation Failed: Transfer doesn't require approval");
    }
  }
}
