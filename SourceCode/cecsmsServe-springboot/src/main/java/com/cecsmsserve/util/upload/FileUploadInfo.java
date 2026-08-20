package com.cecsmsserve.util.upload;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "file")
public class FileUploadInfo {
    private String imageBasePath;
    private String assetBasePath;
    private String legacyAssetBasePath;

    public String getImageBasePath() {
        return imageBasePath;
    }

    public void setImageBasePath(String imageBasePath) {
        this.imageBasePath = imageBasePath;
    }

    public String getAssetBasePath() {
        return assetBasePath;
    }

    public void setAssetBasePath(String assetBasePath) {
        this.assetBasePath = assetBasePath;
    }

    public String getLegacyAssetBasePath() {
        return legacyAssetBasePath;
    }

    public void setLegacyAssetBasePath(String legacyAssetBasePath) {
        this.legacyAssetBasePath = legacyAssetBasePath;
    }
}
