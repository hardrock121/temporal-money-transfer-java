package io.temporal.samples.moneytransfer;

public enum TransferState {
    STARTING("starting"),
    RUNNING("running"),
    WAITING("waiting"),
    FINISHED("finished");

    private final String value;

    TransferState(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static TransferState fromValue(String value) {
        for (TransferState state : values()) {
            if (state.value.equals(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("Unknown transfer state: " + value);
    }
} 