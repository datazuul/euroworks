package com.datazuul.euroworks.apps;

public class Contact {
    private String lastName = "";
    private String firstName = "";
    private String address = "";
    private String phone1 = ""; // Privat
    private String phone2 = ""; // Beruflich
    private String phone3 = ""; // Handy
    private String phone4 = ""; // Fax
    private String phone5 = ""; // Tel
    private String phone6 = ""; // Privat 2
    
    // Custom phone labels
    private String phone1Type = "Privat";
    private String phone2Type = "Beruflich";
    private String phone3Type = "Handy";
    private String phone4Type = "Fax";
    private String phone5Type = "Tel";
    private String phone6Type = "Privat 2";

    private String email = "";
    private String notes = "";

    public Contact() {
    }

    public Contact(String name, String address, String phone1, String phone2, String phone3,
                   String phone4, String phone5, String phone6, String email, String notes) {
        setName(name);
        this.address = address != null ? address : "";
        this.phone1 = phone1 != null ? phone1 : "";
        this.phone2 = phone2 != null ? phone2 : "";
        this.phone3 = phone3 != null ? phone3 : "";
        this.phone4 = phone4 != null ? phone4 : "";
        this.phone5 = phone5 != null ? phone5 : "";
        this.phone6 = phone6 != null ? phone6 : "";
        this.email = email != null ? email : "";
        this.notes = notes != null ? notes : "";
    }

    public String getName() {
        if (lastName.isEmpty()) return firstName;
        if (firstName.isEmpty()) return lastName;
        return lastName + ", " + firstName;
    }

    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            this.lastName = "";
            this.firstName = "";
            return;
        }
        int comma = name.indexOf(',');
        if (comma >= 0) {
            this.lastName = name.substring(0, comma).trim();
            this.firstName = name.substring(comma + 1).trim();
        } else {
            this.lastName = name.trim();
            this.firstName = "";
        }
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName != null ? lastName.trim() : "";
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName != null ? firstName.trim() : "";
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address != null ? address : "";
    }

    // Deprecated but kept for older files compatibility mapping
    public String getPhone() {
        return phone1;
    }

    public void setPhone(String phone) {
        this.phone1 = phone != null ? phone : "";
    }

    public String getPhone1() {
        return phone1;
    }

    public void setPhone1(String phone1) {
        this.phone1 = phone1 != null ? phone1 : "";
    }

    public String getPhone2() {
        return phone2;
    }

    public void setPhone2(String phone2) {
        this.phone2 = phone2 != null ? phone2 : "";
    }

    public String getPhone3() {
        return phone3;
    }

    public void setPhone3(String phone3) {
        this.phone3 = phone3 != null ? phone3 : "";
    }

    public String getPhone4() {
        return phone4;
    }

    public void setPhone4(String phone4) {
        this.phone4 = phone4 != null ? phone4 : "";
    }

    public String getPhone5() {
        return phone5;
    }

    public void setPhone5(String phone5) {
        this.phone5 = phone5 != null ? phone5 : "";
    }

    public String getPhone6() {
        return phone6;
    }

    public void setPhone6(String phone6) {
        this.phone6 = phone6 != null ? phone6 : "";
    }

    // Phone Type selectors getters and setters
    public String getPhone1Type() {
        return phone1Type;
    }

    public void setPhone1Type(String phone1Type) {
        this.phone1Type = phone1Type != null ? phone1Type : "Privat";
    }

    public String getPhone2Type() {
        return phone2Type;
    }

    public void setPhone2Type(String phone2Type) {
        this.phone2Type = phone2Type != null ? phone2Type : "Beruflich";
    }

    public String getPhone3Type() {
        return phone3Type;
    }

    public void setPhone3Type(String phone3Type) {
        this.phone3Type = phone3Type != null ? phone3Type : "Handy";
    }

    public String getPhone4Type() {
        return phone4Type;
    }

    public void setPhone4Type(String phone4Type) {
        this.phone4Type = phone4Type != null ? phone4Type : "Fax";
    }

    public String getPhone5Type() {
        return phone5Type;
    }

    public void setPhone5Type(String phone5Type) {
        this.phone5Type = phone5Type != null ? phone5Type : "Tel";
    }

    public String getPhone6Type() {
        return phone6Type;
    }

    public void setPhone6Type(String phone6Type) {
        this.phone6Type = phone6Type != null ? phone6Type : "Privat 2";
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email != null ? email : "";
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes != null ? notes : "";
    }

    @Override
    public String toString() {
        return getName();
    }
}
