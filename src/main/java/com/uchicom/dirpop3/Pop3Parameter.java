/**
 * (c) 2014 uchicom
 */
package com.uchicom.dirpop3;

import java.io.PrintStream;

import com.uchicom.server.Parameter;
import com.uchicom.server.Server;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class Pop3Parameter extends Parameter {

    public Pop3Parameter(String[] args) {
    	super(args);
    }

    /**
     * 初期化
     * @param ps
     * @return
     */
    public boolean init(PrintStream ps) {
    	// メールボックスの基準フォルダ
    	if (!is("dir")) {
    		put("dir", Constants.DEFAULT_MAILBOX);
    	}
        // 実行するサーバのタイプ
    	if (!is("type")) {
    		put("type", "single");
    	}
    	// ホスト名
    	if (!is("host")) {
    		put("host", "localhost");
    	}
    	// 待ち受けポート
    	if (!is("port")) {
    		put("port", Constants.DEFAULT_PORT);
    	}
    	// 受信する接続 (接続要求) のキューの最大長
    	if (!is("back")) {
    		put("back", Constants.DEFAULT_BACK);
    	}
    	// プールするスレッド数
    	if (!is("pool")) {
    		put("pool", Constants.DEFAULT_POOL);
    	}

        return true;
    }

    public Server createServer() {
    	Server server = null;
		switch (get("type")) {
		case "multi":
			server = new MultiPop3Server(this);
		case "pool":
			server = new PoolPop3Server(this);
			break;
		case "single":
			server = new SinglePop3Server(this);
			break;
		case "selector":
			server = new SelectorPop3Server(this);
			break;
		}
    	return server;
    }
}
