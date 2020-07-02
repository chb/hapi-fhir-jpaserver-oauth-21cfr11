package net.atos.ari.cdr.starter.immudb;

import org.junit.Before;
import org.junit.Test;
import org.junit.*;

import static org.apache.commons.lang3.StringUtils.contains;

public class ImmudbAPITest {

    ImmudbAPI immudbAPI;


    @Before
    public void setUp() throws Exception {
        immudbAPI = new ImmudbAPI("immu", "immu", "http://localhost:3323");
    }

    @Test
    public void addToJournal() {
        String responseJSON = immudbAPI.addToJournal("foo", "bar");
        System.out.println("JSON returned: "+  responseJSON);
        assert (contains( responseJSON, "index"));
    }
}