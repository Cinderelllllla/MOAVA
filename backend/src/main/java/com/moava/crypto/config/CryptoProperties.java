package com.moava.crypto.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component 
@ConfigurationProperties(prefix = "moava.crypto")
public class CryptoProperties {
    private String kekBase64;

    public String getKekBase64(){
        return kekBase64;
    }

    public void setKekBase64(String kekBase64){
        this.kekBase64 = kekBase64;
    }
}
