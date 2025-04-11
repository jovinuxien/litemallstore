package org.linlinjava.litemall.core.infrastructure.acl.dto.cjdropshipdto.authentication;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

public class CjAuthResponseData {

    private String accessToken;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime accessTokenExpiryDate;
    private String refreshToken;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime refreshTokenExpiryDate;
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime createDate;


    public CjAuthResponseData() {
    }

    public CjAuthResponseData(String accessToken, OffsetDateTime accessTokenExpiryDate, String refreshToken, OffsetDateTime refreshTokenExpiryDate) {
        this.accessToken = accessToken;
        this.accessTokenExpiryDate = accessTokenExpiryDate;
        this.refreshToken = refreshToken;
        this.refreshTokenExpiryDate = refreshTokenExpiryDate;
        this.createDate = OffsetDateTime.now();
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public OffsetDateTime getAccessTokenExpiryDate() {
        return accessTokenExpiryDate;
    }

    public void setAccessTokenExpiryDate(OffsetDateTime accessTokenExpiryDate) {
        this.accessTokenExpiryDate = accessTokenExpiryDate;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }

    public OffsetDateTime getRefreshTokenExpiryDate() {
        return refreshTokenExpiryDate;
    }

    public void setRefreshTokenExpiryDate(OffsetDateTime refreshTokenExpiryDate) {
        this.refreshTokenExpiryDate = refreshTokenExpiryDate;
    }

    public OffsetDateTime getCreateDate() {
        return createDate;
    }

    public void setCreateDate(OffsetDateTime createDate) {
        this.createDate = createDate;
    }
}
