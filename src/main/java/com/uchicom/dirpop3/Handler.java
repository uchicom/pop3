/**
 * (c) 2013 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.security.NoSuchAlgorithmException;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public interface Handler {

    public void handle(SelectionKey key) throws IOException, NoSuchAlgorithmException;
}
