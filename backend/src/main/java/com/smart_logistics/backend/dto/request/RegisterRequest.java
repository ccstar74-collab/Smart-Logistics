package com.smart_logistics.backend.dto.request;

import com.smart_logistics.backend.enums.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "username is required")
    @Size(max = 50, message = "username must not exceed 50 characters")
    private String username;

    @NotBlank(message = "password is required")
    @Size(min = 8, max = 72, message = "password length must be between 8 and 72 characters")
    private String password;

    @NotBlank(message = "name is required")
    @Size(max = 50, message = "name must not exceed 50 characters")
    private String name;

    @Size(max = 20, message = "phone must not exceed 20 characters")
    private String phone;

    @NotNull(message = "role is required")
    private UserRole role;

    @Size(max = 100, message = "companyName must not exceed 100 characters")
    private String companyName;

    @Size(max = 50, message = "contactPerson must not exceed 50 characters")
    private String contactPerson;

    @Size(max = 50, message = "licenseNo must not exceed 50 characters")
    private String licenseNo;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public UserRole getRole() { return role; }
    public void setRole(UserRole role) { this.role = role; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }
    public String getLicenseNo() { return licenseNo; }
    public void setLicenseNo(String licenseNo) { this.licenseNo = licenseNo; }
}
