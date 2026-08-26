package com.smart_logistics.backend.service.eta;

public class EtaProviderException extends RuntimeException {

    public EtaProviderException(String message) {
        super(message);
    }

    public EtaProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
