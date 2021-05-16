package at.co.schroetter.security;

@SuppressWarnings("unused")
public class RuntimeException extends java.lang.RuntimeException
{
	public RuntimeException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public RuntimeException(String message)
	{
		super(message);
	}

	public RuntimeException() { super(); }
}
