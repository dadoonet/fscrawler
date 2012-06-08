package org.elasticsearch.river.fs;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class to sign *things*
 * @author David Pilato (aka dadoonet)
 */
public class SignTool {

	public static String sign(String toSign) throws NoSuchAlgorithmException {
	
		MessageDigest md = MessageDigest.getInstance("MD5");
		md.update(toSign.getBytes());
	
		String key = "";
		byte b[] = md.digest();
		for (int i = 0; i < b.length; i++) {
			long t = b[i] < 0 ? 256 + b[i] : b[i];
			key += Long.toHexString(t);
		}
	
		return key;
	}

}
