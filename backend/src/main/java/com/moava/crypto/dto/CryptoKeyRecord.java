package com.moava.crypto.dto;

import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;

@Setter 
@Getter 
public class CryptoKeyRecord {
    
    private Integer keyVer;
    private String algorithm;
    private String encryptedDek;
    private String keyHash;
    private String status;
    private LocalDate activatedAt;
    private LocalDate retiredAt;
}
