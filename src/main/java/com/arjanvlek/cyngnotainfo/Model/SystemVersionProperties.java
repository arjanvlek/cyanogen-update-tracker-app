package com.arjanvlek.cyngnotainfo.Model;

public class SystemVersionProperties {

    private String cyanogenOSVersion;
    private String securityPatchDate;

    public String getCyanogenOSVersion() {
        return cyanogenOSVersion;
    }

    public void setCyanogenOSVersion(String cyanogenOSVersion) {
        this.cyanogenOSVersion = cyanogenOSVersion;
    }

    public String getSecurityPatchDate() {
        return securityPatchDate;
    }

    public void setSecurityPatchDate(String securityPatchDate) {
        this.securityPatchDate = securityPatchDate;
    }
}
