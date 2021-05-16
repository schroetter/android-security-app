package at.co.schroetter.security;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Telephony.Sms.Intents;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

public class MessageListener extends BroadcastReceiver
{
	private static final String TAG = MessageListener.class.getName();

	private static final String SMS_RECEIVED_ACTION = "android.provider.Telephony.SMS_RECEIVED";

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if(!SMS_RECEIVED_ACTION.equals(intent.getAction()))
		{
			return;
		}

		for(SmsMessage smsMessage : Intents.getMessagesFromIntent(intent))
		{
			SharedPreferences pref = context.getSharedPreferences(context.getString(R.string.preference), Context.MODE_PRIVATE);
			int counter = pref.getInt(context.getString(R.string.preference_messages), 0) + 1;

			pref.edit()
				.putLong(context.getString(R.string.preference_timestamp), System.currentTimeMillis())
				.putInt(context.getString(R.string.preference_messages), counter)
				.apply();

			String messageBody = smsMessage.getMessageBody().trim();
			String originatingAddress = smsMessage.getOriginatingAddress().trim();

			if(messageBody.length() < PasswordLimits.LENGTH_MIN || messageBody.length() > PasswordLimits.LENGTH_MAX)
			{
				Log.d(TAG, "onReceive: Message exceeds internal size limits! Skipping.");
				continue;
			}

			String hash = pref.getString(context.getString(R.string.preference_hash), null);
			String salt = pref.getString(context.getString(R.string.preference_salt), null);

			if(hash == null || hash.length() <= 0 || salt == null || salt.length() <= 0)
			{
				Log.d(TAG, "onReceive: Could not retrieve hash or salt from shared preferences! Skipping.");
				continue;
			}
			else if(!PasswordHash.check(messageBody, hash, salt))
			{
				Log.d(TAG, "onReceive: Hash did not match! Skipping.");
				continue;
			}

			DevicePolicyManager devicePolicyManager = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

			if(!devicePolicyManager.isAdminActive(new ComponentName(context, DeviceAdminReceiver.class)))
			{
				Log.d(TAG, "onReceive: Not an active device admin! Skipping.");
				continue;
			}

			try
			{
				if(pref.getBoolean(context.getString(R.string.preference_wipe), false))
				{
					Log.i(TAG, "onReceive: Hash matched! Locking device...");
					devicePolicyManager.lockNow();

					sendSMS(context, originatingAddress, "Success! Wiping device...");
					Log.i(TAG, "onReceive: Wiping device and external storage now!");

					/*
					try
					{
						File[] dirs = context.getExternalFilesDirs(null);
						File parent;
						File dir;

						//noinspection ForLoopReplaceableByForEach
						for(int i = 0; i < dirs.length; i++)
						{
							dir = dirs[i];

							Log.d(TAG, "onReceive: Checking external files directory: " + dir);

							try
							{
								while((parent = dir.getParentFile()) != null)
								{
									Log.d(TAG, "onReceive: Checking parent directory: " + parent);

									if(parent.canRead() && parent.canWrite())
									{
										dir = parent;
										continue;
									}

									break;
								}
							}
							catch(Throwable e)
							{
								Log.e(TAG, "onReceive: Parent check failed: " + dir, e);
							}
							finally
							{
								if(dir != null && dir.canRead() && dir.canWrite())
								{
									Log.i(TAG, "onReceive: Deleting directory: " + dir);
									recursiveDelete(dir);
								}
								else
								{
									Log.w(TAG, "onReceive: Not deleting directory: " + dir);
								}
							}
						}
					}
					catch(Throwable e)
					{
						Log.e(TAG, "onReceive: Could not delete files at external storage(s)!", e);
					}
					*/

					try
					{
						Log.d(TAG, "onReceive: Wiping device with WIPE_EXTERNAL_STORAGE flag...");
						devicePolicyManager.wipeData(DevicePolicyManager.WIPE_EXTERNAL_STORAGE);
					}
					catch(Throwable e1)
					{
						try
						{
							Log.d(TAG, "onReceive: Retrying without flags...");
							devicePolicyManager.wipeData(0);
						}
						catch(Throwable e2)
						{
							try
							{
								e2.initCause(e1);
							}
							catch(IllegalStateException e3)
							{
								Log.e(TAG, "onReceive: DEVICE WIPE FAILED! (#1)", e1);
							}

							Log.e(TAG, "onReceive: DEVICE WIPE FAILED! (#2)", e2);
						}
					}
				}
				else
				{
					Log.i(TAG, "onReceive: Evaluation mode active! Device not wiped ;-)");
					sendSMS(context, originatingAddress, "Success! (Test Mode)");

					String channelID = context.getString(R.string.notification_evaluation_id);
					CharSequence channelName = context.getString(R.string.notification_evaluation_name);
					NotificationManager mNM = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
					String notificationText = String.format(context.getString(R.string.evaluation_toast), originatingAddress);

					if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
					{
						mNM.createNotificationChannel(new NotificationChannel(channelID, channelName, NotificationManager.IMPORTANCE_HIGH));
					}

					mNM.notify(counter, new NotificationCompat.Builder(context, channelID)
						.setStyle(new NotificationCompat.BigTextStyle().bigText(notificationText))
						.setContentTitle(context.getString(R.string.notification_evaluation_name))
						.setPriority(NotificationCompat.PRIORITY_MAX)
						.setSmallIcon(android.R.drawable.ic_secure)
						.setContentText(notificationText)
						.build()
					);
				}
			}
			catch(Throwable e)
			{
				Log.e(TAG, "onReceive: Oops! Sorry, bro.", e);
				sendSMS(context, originatingAddress, e.toString());
			}
		}
	}

	@SuppressWarnings("UnusedReturnValue")
	private static boolean sendSMS(Context context, String destinationAddress, String text)
	{
		SmsManager smsManager;

		try
		{
			if(ContextCompat.checkSelfPermission(context, "android.permission.SEND_SMS") != PackageManager.PERMISSION_GRANTED)
			{
				throw new RuntimeException("SEND_SMS permissions not granted!");
			}

			// getSystemService() is buggy with dual SIM?!
			// Would be the preferred method on Android 12.
			smsManager = SmsManager.getDefault();

			if(smsManager == null)
			{
				throw new RuntimeException("Could not get instance of SmsManager!");
			}
			else
			{
				Log.i(TAG, String.format("sendSMS: Sending SMS (length=%d) to %s...", (text != null ? text.length() : -1), destinationAddress));
				smsManager.sendTextMessage(destinationAddress, null, text, null, null);
				return true;
			}
		}
		catch(Throwable e)
		{
			Log.e(TAG, "sendSMS: Could not send SMS!", e);
		}

		return false;
	}

	/*
	protected static boolean recursiveDelete(File file)
	{
		boolean state = true;

		try
		{
			if(file.isFile())
			{
				if(file.delete())
				{
					Log.w(TAG, "recursiveDelete: DELETED: " + file);
				}
				else
				{
					state = false;
				}
			}
			else if(file.isDirectory())
			{
				File[] children = file.listFiles();

				if(children != null)
				{
					for(File child : children)
					{
						if(!recursiveDelete(child))
						{
							state = false;
						}
					}
				}
			}
		}
		catch(Throwable e)
		{
			Log.e(TAG, "recursiveDelete: FAILED: " + file, e);
			return false;
		}

		if(!state)
		{
			Log.e(TAG, "recursiveDelete: FAILED: " + file);
			return false;
		}

		return true;
	}
	*/
}
