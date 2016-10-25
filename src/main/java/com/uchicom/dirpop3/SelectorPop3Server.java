/**
 * (c) 2013 uchicom
 */
package com.uchicom.dirpop3;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;

import com.uchicom.server.AbstractSelectorServer;
import com.uchicom.server.Handler;
import com.uchicom.server.Parameter;

/**
 * Channelを利用したPOP3サーバー
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class SelectorPop3Server extends AbstractSelectorServer {


	private static boolean alive = true;

	/**
	 * @param parameter
	 */
	public SelectorPop3Server(Parameter parameter) {
		super(parameter, new Pop3HandlerFactory(parameter));
	}


	/**
	 * メイン処理
	 *
	 */
	@Override
	protected void execute(Selector selector) throws IOException{
		while (alive) {
			if (selector.select() > 0) {
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> ite = keys.iterator();
				while (ite.hasNext()) {
					SelectionKey key = ite.next();
					ite.remove();
					try {
						if (key.isValid()) {
							((Handler) key.attachment()).handle(key);
						} else {
							key.cancel();
						}
					} catch (IOException e) {
						e.printStackTrace();
						key.cancel();
					}
				}
			}
		}
	}


}
