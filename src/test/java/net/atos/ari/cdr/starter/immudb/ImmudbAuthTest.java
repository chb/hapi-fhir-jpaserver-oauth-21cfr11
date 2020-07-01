package net.atos.ari.cdr.starter.immudb;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ImmudbAuthTest {

    ImmudbAuth immudbAuth ;

    @Before
    public void setUp() throws Exception {
        immudbAuth = new ImmudbAuth("immu", "immu");
    }

    @Test
    public void addToJournal() {
        immudbAuth.addToJournal("foo", "bar");

    }
}