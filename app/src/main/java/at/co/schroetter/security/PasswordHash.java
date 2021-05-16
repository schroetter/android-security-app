package at.co.schroetter.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

abstract public class PasswordHash
{
	static final public String DEFAULT_ALGORITHM = "SHA-512";

	public static String calculate(String password, String salt)
	{
		return calculate(password, salt, null);
	}

	public static String calculate(String password, String salt, String algorithm)
	{
		byte[] bytes;
		MessageDigest md;
		StringBuilder sb;
		String hash = null;

		if(algorithm == null)
		{
			algorithm = DEFAULT_ALGORITHM;
		}

		try
		{
			md = MessageDigest.getInstance(algorithm);
			md.update(salt.getBytes());

			bytes = md.digest(password.getBytes());
			sb = new StringBuilder();

			for(byte aByte : bytes)
			{
				sb.append(Integer.toString((aByte & 0xff) + 0x100, 16).substring(1));
			}

			hash = sb.toString();
		}
		catch(NoSuchAlgorithmException e)
		{
			e.printStackTrace();
		}

		return hash;
	}

	public static boolean check(String passphrase, String hash, String salt)
	{
		return check(passphrase, hash, salt, null);
	}

	public static boolean check(String passphrase, String hash, String salt, String algorithm)
	{
		if(hash == null || passphrase == null || hash.length() <= 0 || passphrase.length() <= 0)
		{
			return false;
		}

		return MessageDigest.isEqual(hash.getBytes(), calculate(passphrase, salt, algorithm).getBytes());
	}
}
