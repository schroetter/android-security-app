package at.co.schroetter.security;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.UUID;

public class MainActivity extends AppCompatActivity
{
	final protected static String   TAG         = MainActivity.class.getName();

//	protected final static int REQUEST_NONE        = -1;
	protected final static int REQUEST_PERMISSIONS =  1;
	protected final static int REQUEST_DEVICEADMIN =  2;

	View                view;
	SharedPreferences   pref;
	DevicePolicyManager mDPM;
	ComponentName       mDAC;

	TextView tState;
	TextView tLastMessage;
	TextView tMessageCounter;

	EditText iPassphrase;
	EditText iConfirm;

	boolean enableStatusUpdates  = true;
	boolean enableSwitchListener = true;

	SwitchCompat sPermissions;
	SwitchCompat sDeviceAdmin;
	SwitchCompat sWipe;
	View         vPW;
	Button       bDelete;
	Button       bUpdate;

	DateFormat dateFormat;
	DateFormat timeFormat;

	/*
	private static void DEBUG(File file)
	{
		File[] children = file.listFiles();

		if(children != null)
		{
			for(File child : children)
			{
				DEBUG(child);
			}
		}

		int mode = 0 | (file.canRead() ? 1 : 0) | (file.canWrite() ? 2 : 0) | (file.canExecute() ? 4 : 0);
		Log.v(TAG, String.format("%2$s%3$s%3$s%3$s: %1$s", file, file.isDirectory() ? "D" : "-", mode));
	}
	*/

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		view = findViewById(android.R.id.content);

		setContentView(R.layout.activity_main);
		setTitle(getString(R.string.app_title));
		Context context = getBaseContext();

		pref = context.getSharedPreferences(getString(R.string.preference), Context.MODE_PRIVATE);
		dateFormat = android.text.format.DateFormat.getDateFormat(context);
		timeFormat = android.text.format.DateFormat.getTimeFormat(context);

		// Device Admin variables
		mDPM = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
		mDAC = new ComponentName(this, DeviceAdminReceiver.class);

		// Status elements
		tState          = findViewById(R.id.status_state);
		tLastMessage    = findViewById(R.id.status_timestamp);
		tMessageCounter = findViewById(R.id.status_messages);

		// Main settings (switch buttons)
		sDeviceAdmin = findViewById(R.id.switch_deviceadmin);
		sPermissions = findViewById(R.id.switch_permissions);
		sWipe        = findViewById(R.id.switch_wipe);

		// Passphrase stuff
		vPW          = findViewById(R.id.password);
		iPassphrase  = findViewById(R.id.passphrase1);
		iConfirm     = findViewById(R.id.passphrase2);
		bDelete      = findViewById(R.id.button_delete);
		bUpdate      = findViewById(R.id.button_update);

		if(sPermissions != null)
		{
			sPermissions.setOnCheckedChangeListener((buttonView, isChecked) ->
			{
				if(!enableSwitchListener)
				{
					Log.d(TAG, "Permissions.onCheckedChangeListener: Switch listener not enabled.");
					return;
				}

				if(isChecked && !hasPermissions())
				{
					String[] permissions;

					if(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
					{
						permissions = new String[] {Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS};
					}
					else
					{
						permissions = new String[] {Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
					}

					Log.i(TAG, "Permissions.onCheckedChangeListener: Requesting permissions...");
					ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
				}
				else
				{
					Log.d(TAG, "Permissions.onCheckedChangeListener: Switch not checked or permissions already granted.");
				}
			});
		}

		if(sDeviceAdmin != null)
		{
			sDeviceAdmin.setOnCheckedChangeListener((buttonView, isChecked) ->
			{
				if(!enableSwitchListener)
				{
					Log.d(TAG, "DeviceAdmin.onCheckedChangeListener: Switch listener not enabled.");
					return;
				}

				try
				{
					if(sWipe != null)
					{
						// Don't change enableSwitchListener here!
						// It's desired to reset wipe preference.
						enableStatusUpdates = false;
						sWipe.setEnabled(false);
						sWipe.setChecked(false);
					}
				}
				finally
				{
					enableStatusUpdates = true;
				}

				if(isChecked)
				{
					Log.i(TAG, "DeviceAdmin.onCheckedChangeListener: Requesting device admin...");
					Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
					// intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, null);
					intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, mDAC);
					startActivityForResult(intent, REQUEST_DEVICEADMIN);
				}
				else
				{
					// TODO: Sadly there's no listener for this state change. This leads to crazy UI responses.
					//       One of them is an auto-switch-on of the Device Admin button. See following workaround.
					//       This needs a better final solution! (Reproduce: wipe=enabled & disable DA switch)
					Log.i(TAG, "DeviceAdmin.onCheckedChangeListener: Removing device admin...");
					mDPM.removeActiveAdmin(mDAC);
					resetState();

					if(vPW != null)
					{
						// Only a stupid workaround. See comment above.
						vPW.setVisibility(View.GONE);
					}
				}
			});
		}

		if(sWipe != null)
		{
			sWipe.setOnCheckedChangeListener((buttonView, isChecked) ->
			{
				if(!enableSwitchListener)
				{
					Log.d(TAG, "Wipe.onCheckedChangeListener: Switch listener not enabled.");
					return;
				}

				// --------------------------------------------------------- //
				// DON'T DELETE THE SECRET HASH AT THIS POINT! JUST KEEP IT. //
				// --------------------------------------------------------- //

				if(isChecked)
				{
					Log.d(TAG, "Wipe.onCheckedChangeListener: Setting wipe preference to TRUE...");
					pref.edit().putBoolean(getString(R.string.preference_wipe), true).apply();
				}
				else
				{
					Log.d(TAG, "Wipe.onCheckedChangeListener: Removing wipe preference...");
					pref.edit().remove(getString(R.string.preference_wipe)).apply();
				}

				clearPassphraseInputs();
				updateStatus();
				informUser();
			});
		}
		
		if(bDelete != null)
		{
			bDelete.setOnClickListener((View v) ->
			{
				hideKeyboard();
				new AlertDialog.Builder(this)
					.setMessage(getString(R.string.delete_passphrase_confirmation))
					.setPositiveButton(android.R.string.ok, (dialog, which) ->
					{
						pref.edit().remove(getString(R.string.preference_hash)).apply();
						Log.i(TAG, "Delete.onClickListener: Passphrase deleted.");
						showSuccess(getString(R.string.passphrase_deleted));
						clearPassphraseInputs();
						//updateStatus();

						if(sWipe != null)
						{
							sWipe.setChecked(false);
						}
					})
					.setNegativeButton(android.R.string.cancel, null)
					.show();
			});
		}

		if(bUpdate != null)
		{
			bUpdate.setOnClickListener((View v) ->
			{
				if(iPassphrase == null || iConfirm == null)
				{
					return;
				}

				String passphrase = iPassphrase.getText().toString().trim();
				String confirm = iConfirm.getText().toString().trim();

				if(!passphrase.equals(confirm))
				{
					Log.i(TAG, "Update.onClickListener: Passphrase mismatch.");
					showError(getString(R.string.passphrase_mismatch));
					return;
				}
				else if(passphrase.length() < PasswordLimits.LENGTH_MIN || passphrase.length() > PasswordLimits.LENGTH_MAX)
				{
					Log.i(TAG, "Update.onClickListener: Passphrase length check failed.");
					showError(String.format(getString(R.string.passphrase_invalid), PasswordLimits.LENGTH_MIN, PasswordLimits.LENGTH_MAX));
					return;
				}

				SharedPreferences.Editor edit = pref.edit();

				try
				{
					String salt = UUID.randomUUID().toString();
					String hash = PasswordHash.calculate(passphrase, salt);

					edit.putString(getString(R.string.preference_salt), salt);
					edit.putString(getString(R.string.preference_hash), hash);

					Log.i(TAG, "Update.onClickListener: Passphrase saved.");
					showSuccess(getString(R.string.passphrase_updated));
				}
				catch(Throwable e)
				{
					e.printStackTrace();
					edit.remove(getString(R.string.preference_hash));
					edit.remove(getString(R.string.preference_salt));
					showError(e.toString());
				}
				finally
				{
					edit.apply();
					clearPassphraseInputs();
					updateStatus();
				}
			});
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		getMenuInflater().inflate(R.menu.menu_main, menu);

		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if(item.getItemId() == R.id.refresh)
		{
			updateStatus();
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		Log.d(TAG, String.format("onActivityResult: requestCode=%d resultCode=%d", requestCode, resultCode));

		if(requestCode == REQUEST_DEVICEADMIN)
		{
			// resultCode == RESULT_OK
			// resultCode == RESULT_CANCELED
			updateStatus();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
	{
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		for(int i = 0; i < permissions.length; i++)
		{
			Log.d(TAG, String.format("onRequestPermissionsResult: requestCode=%d permissions=%s grantResult=%d", requestCode, permissions[i], grantResults[i]));
		}

		if(requestCode == REQUEST_PERMISSIONS)
		{
			// grantResults[i] == PackageManager.PERMISSION_GRANTED
			updateStatus();
			informUser();
		}
	}

	@Override
	public void onResume()
	{
		super.onResume();
		updateStatus();
	}

	private void resetState()
	{
		if(tState != null)
		{
			tState.setText(getString(R.string.status_state_inactive));
		}
	}

	private synchronized void updateStatus()
	{
		if(!enableStatusUpdates)
		{
			Log.d(TAG, "updateStatus: Status updates disabled.");
			return;
		}

		enableSwitchListener = false;

		try
		{
			boolean permissions = hasPermissions();
			boolean admin       = (permissions && isDeviceAdmin());
			boolean wipe        = (admin       && isWipeEnabled());
			boolean hash        = (admin       && hasSaltedHash());

			boolean enabled = (permissions && admin && hash);

			Log.d(TAG, String.format("updateStatus: enabled=%s (permissions=%s admin=%s hash=%s) wipe=%s", enabled, permissions, admin, hash, wipe));

			if(enabled && tState != null)
			{
				if(wipe)
				{
					tState.setText(getString(R.string.status_state_active).toUpperCase());
				}
				else
				{
					tState.setText(getString(R.string.status_state_ready));
				}
			}
			else
			{
				resetState();
			}

			if(tLastMessage != null)
			{
				long timestamp = pref.getLong(getString(R.string.preference_timestamp), -1L);

				if(enabled && timestamp >= 0)
				{
					tLastMessage.setText(String.format(getString(R.string.status_datetime), dateFormat.format(timestamp), timeFormat.format(timestamp)));
				}
				else
				{
					tLastMessage.setText(getString(R.string.empty));
				}
			}

			if(tMessageCounter != null)
			{
				int counter = pref.getInt(getString(R.string.preference_messages), 0);

				if(enabled && counter > 0)
				{
					tMessageCounter.setText(NumberFormat.getInstance().format(counter));
				}
				else
				{
					tMessageCounter.setText(getString(R.string.empty));
				}
			}

			if(sPermissions != null)
			{
				sPermissions.setEnabled(!permissions);
				sPermissions.setChecked(permissions);
			}

			if(sDeviceAdmin != null)
			{
				sDeviceAdmin.setEnabled(permissions);
				sDeviceAdmin.setChecked(permissions && admin);
			}

			if(sWipe != null)
			{
				sWipe.setEnabled(permissions && admin);
				sWipe.setChecked(wipe);
			}

			if(vPW != null)
			{
				vPW.setVisibility(wipe ? View.VISIBLE : View.GONE);

				if(bDelete != null)
				{
					bDelete.setVisibility(hash ? View.VISIBLE : View.INVISIBLE);
				}

				if(bUpdate != null)
				{
					bUpdate.setText(getString(hash ? R.string.update_passphrase : R.string.add_passphrase));
				}
			}
		}
		finally
		{
			enableSwitchListener = true;
		}
	}

	protected boolean isWipeEnabled()
	{
		return pref.getBoolean(getString(R.string.preference_wipe), false);
	}

	protected boolean isDeviceAdmin()
	{
		return mDPM.isAdminActive(mDAC);
	}

	protected boolean hasSaltedHash()
	{
		String hash = pref.getString(getString(R.string.preference_hash), null);
		String salt = pref.getString(getString(R.string.preference_salt), null);

		return !(hash == null || hash.length() <= 0 || salt == null || salt.length() <= 0);
	}

	protected boolean hasPermissions()
	{
		return (/*canReadStorage() && canWriteStorage() &&*/ canReceiveSMS() && canSendSMS());
	}

	/*
	public boolean canReadStorage()
	{
		return (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(getBaseContext(), "android.permission.READ_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED);
	}

	public boolean canWriteStorage()
	{
		return (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(getBaseContext(), "android.permission.WRITE_EXTERNAL_STORAGE") == PackageManager.PERMISSION_GRANTED);
	}
	*/

	public boolean canReceiveSMS()
	{
		return (ContextCompat.checkSelfPermission(getBaseContext(), "android.permission.RECEIVE_SMS") == PackageManager.PERMISSION_GRANTED);
	}

	public boolean canSendSMS()
	{
		return (ContextCompat.checkSelfPermission(getBaseContext(), "android.permission.SEND_SMS") == PackageManager.PERMISSION_GRANTED);
	}

	private void showError(String msg)
	{
		Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
		hideKeyboard();
	}

	private void showSuccess(String msg)
	{
		Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
		hideKeyboard();
	}

	private void hideKeyboard()
	{
		((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(view.getWindowToken(), 0);
	}

	private void clearPassphraseInputs()
	{
		if(iPassphrase != null && iConfirm != null)
		{
			iPassphrase.setText(null);
			iConfirm.setText(null);
		}
	}

	private void informUser()
	{
		if(!isWipeEnabled())
		{
			Log.d(TAG, "informUser: Wipe feature disabled.");
		}
		else if(!hasPermissions() || !isDeviceAdmin() || !hasSaltedHash())
		{
			Log.i(TAG, "informUser: Wipe feature enabled, but no passphrase set!");
		}
		else
		{
			Log.i(TAG, "informUser: Wipe feature enabled.");
			showSuccess(getString(R.string.passphrase_reused));
		}
	}
}
