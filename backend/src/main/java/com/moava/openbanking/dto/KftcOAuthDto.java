package com.moava.openbanking.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

public class KftcOAuthDto {

    @Getter @Setter
    public static class AuthorizeRequest {
        @JsonProperty("client_id")
        private String clientId;

        @JsonProperty("redirect_uri")
        private String redirectUri;

        @JsonProperty("scope")
        private String scope; // 예: "login inquiry transfer"

        @JsonProperty("state")
        private String state;

        @JsonProperty("user_seq_no")
        private String userSeqNo;
    }

    @Getter @Setter
    public static class AuthorizeResponse extends KftcBaseResponse {
        @JsonProperty("code")
        private String code;

        @JsonProperty("state")
        private String state;

        @JsonProperty("scope")
        private String scope;

        @JsonProperty("redirect_uri")
        private String redirectUri;

        @JsonProperty("expires_in")
        private long expiresIn;
    }

    @Getter @Setter
    public static class TokenRequest {
        @JsonProperty("grant_type")
        private String grantType; // "authorization_code" 또는 "client_credentials"

        @JsonProperty("client_id")
        private String clientId;

        @JsonProperty("client_secret")
        private String clientSecret;

        @JsonProperty("code")
        private String code; // authorization_code 시 필수

        @JsonProperty("redirect_uri")
        private String redirectUri;

        @JsonProperty("scope")
        private String scope; // client_credentials 시 "oob"
    }

    @Getter @Setter
    public static class TokenResponse extends KftcBaseResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_in")
        private long expiresIn;

        @JsonProperty("scope")
        private String scope;

        @JsonProperty("user_seq_no")
        private String userSeqNo;
    }

    @Getter @Setter
    public static class RevokeResponse extends KftcBaseResponse {
        @JsonProperty("revoked")
        private boolean revoked;
    }
}
