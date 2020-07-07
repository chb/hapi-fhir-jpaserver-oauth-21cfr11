/*
 * Copyright (c) 2020. Boston Children's Hospital. http://www.childrenshospital.org/research/departments-divisions-programs/programs/chip
 * Author: christopher.gentle@childrens.harvard.edu
 */

package net.atos.ari.cdr.starter.immudb;

/**
 *
 * POJO token class to read and write bearer token responses from the ImmuDB gateway
 *
 */
public class ImmudbToken {
    String token;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
