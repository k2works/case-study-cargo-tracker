package com.example.cargotracker.shipper.interfaces.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class ShipperRegisterForm {

    @NotBlank(message = "氏名/社名は必須です")
    @Size(max = 200, message = "氏名/社名は 200 文字以内にしてください")
    private String name;

    @NotBlank(message = "メールアドレスは必須です")
    @Email(message = "メールアドレスの形式が不正です")
    @Size(max = 254)
    private String email;

    @Size(max = 20)
    private String phone;

    @NotNull(message = "荷主種別は必須です")
    private String category = "INDIVIDUAL";

    @Size(max = 50)
    private String contractNumber;

    @DecimalMin(value = "0", message = "割引率は 0 以上にしてください")
    @DecimalMax(value = "30", message = "割引率は 30 以下にしてください")
    private BigDecimal discountRate;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getContractNumber() { return contractNumber; }
    public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }
    public BigDecimal getDiscountRate() { return discountRate; }
    public void setDiscountRate(BigDecimal discountRate) { this.discountRate = discountRate; }
}
