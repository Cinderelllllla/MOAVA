package com.moava.crypto.dto;

import java.time.LocalDate;

public class CryptoKeyRecord {
    
    private Integer keyVer;
    private String algorithm;
    private String encryptedDek;
    private String keyHash;
    private String status;
    private LocalDate activatedAt;
    private LocalDate retiredAt;

    public Integer getKeyVer(){
        return keyVer;
    }

    public void setKeyVer(Integer keyVer){
        this.keyVer = keyVer;
    }

    public String getAlgorithm(){
        return algorithm;
    }

    public void setAlgorithm(String algorithm){
        this.algorithm = algorithm;
    }

    public String getEncryptedDek(){
        return encryptedDek;
    }

    public void setEncryptedDek(String encryptedDek){
        this.encryptedDek = encryptedDek;
    }

    public String getKeyHash(){
        return keyHash;
    }

    public void setKeyHash(String keyHash){
        this.keyHash = keyHash;
    }

    public String getStatus(){
        return status;
    }

    public void setStatus(String status){
        this.status = status;
    }

    public LocalDate getActivatedAt(){
        return activatedAt;
    }

    public void setActivatedAt(LocalDate activatedAt){
        this.activatedAt = activatedAt;
    }

        public LocalDate getRetiredAt(){
        return retiredAt;
    }

    public void setRetiredAt(LocalDate retiredAt){
        this.retiredAt = retiredAt;
    }
}
