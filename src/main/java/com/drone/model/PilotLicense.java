package com.drone.model;

public enum PilotLicense {
    CLASS_1("1종", "최대이륙중량 25kg 초과 자체중량 150kg 이하"),
    CLASS_2("2종", "최대이륙중량 7kg 초과 25kg 이하"),
    CLASS_3("3종", "최대이륙중량 2kg 초과 7kg 이하"),
    CLASS_4("4종", "최대이륙중량 250g 초과 2kg 이하"),
    NONE("자격없음", "250g 이하 비사업용만 가능");

    private final String displayName;
    private final String allowedDroneSpec;

    PilotLicense(String displayName, String allowedDroneSpec) {
        this.displayName = displayName;
        this.allowedDroneSpec = allowedDroneSpec;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAllowedDroneSpec() {
        return allowedDroneSpec;
    }
}
