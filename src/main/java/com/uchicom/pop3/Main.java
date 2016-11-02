/**
 * (c) 2016 uchicom
 */
package com.uchicom.pop3;

/**
 * 起動クラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class Main {

	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		Pop3Parameter parameter = new Pop3Parameter(args);
		if (parameter.init(System.err)) {
			parameter.createServer().execute();
		}
	}

}
