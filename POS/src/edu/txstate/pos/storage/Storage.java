package edu.txstate.pos.storage;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;

import android.content.ContentValues;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;
import edu.txstate.db.POSContract;
import edu.txstate.pos.callback.ServiceCallback;
import edu.txstate.pos.model.Item;
import edu.txstate.pos.model.RemoteCart;
import edu.txstate.pos.model.User;

/**
 * API for persistent storage for the POS application.  This class abstracts
 * the differences in where data is stored - locally or remotely. 
 * 
 * Activities should get an instance of this object from the POSActvity using the
 * getStorage() method.  The setting of the context, deviceID, and logged in user
 * is taken care of when accessed via that method.
 * 
 * @see edu.txstate.pos.POSActivity
 * 
 *
 */
public class Storage {
	
	private static final String LOG_TAG = "STORAGE";

	// Remote storage
	private Ping hb = null;
	private UserRemoteStorage userRemote = null;
	private ItemRemoteStorage itemRemote = null;
	private CartRemoteStorage cartRemote = null;
	
	// Local storage
	private ItemLocalStorage itemLocal = null;
	private SettingsLocalStorage settingLocal = null;
	private CartLocalStorage cartLocal = null;
	
	// Device ID of this device
	private String mDeviceID = null;
	// Logged in user
	private User updUser = null;
	
	// Sync Service
	private ServiceCallback syncService = null;
	
	// SQLiteDatabase of local storage
	private SQLiteDatabase db = null;
	
	
	/**
	 * Constructor.
	 * 
	 * @param context  		Android Context object
	 * @param deviceID		Unique device ID used for synchronization
	 * @param loggedInUser	The currently logged in user (for logging)
	 */
	public Storage(SQLiteDatabase db, String deviceID, User loggedInUser, ServiceCallback syncService) {
		mDeviceID = deviceID;
		updUser = loggedInUser;
		this.syncService = syncService;
		this.db = db;
		
		// Remote storage objects
		hb = new Ping(mDeviceID);
		userRemote = new UserRemoteStorage(mDeviceID);
		itemRemote = new ItemRemoteStorage(mDeviceID);
		cartRemote = new CartRemoteStorage(mDeviceID);
		
		// Local storage objects
		itemLocal = new ItemLocalStorage(db);
		settingLocal = new SettingsLocalStorage(db);
		cartLocal = new CartLocalStorage(db);
	}
	
	/**
	 * @return the deviceID
	 */
	public String getDeviceID() {
		return mDeviceID;
	}

	/**
	 * Used when the logged in user changes.
	 * 
	 * @param loggedInUser
	 */
	public void setLoggedInUser(User loggedInUser) {
		updUser = loggedInUser;
	}
	
	/* ++++++++++++++++++++++++++++++++++++++++++++++++
	 * PING
	 * ++++++++++++++++++++++++++++++++++++++++++++++++
	 */
	
	/**
	 * Simple ping of the POS server.
	 * 
	 * @return True if the POS services are available.
	 */
	public boolean ping() {
		return hb.ping();
	}

	/* ++++++++++++++++++++++++++++++++++++++++++++++++
	 * CART
	 * ++++++++++++++++++++++++++++++++++++++++++++++++
	 */
	
	public List<RemoteCart> getPushableCarts() throws NoCartFoundException, StorageException {
		List<RemoteCart> carts = new ArrayList<RemoteCart>();
			List<Long> ids = cartLocal.getPushableCarts();
			for (Long id : ids) {
				RemoteCart cart = new RemoteCart(db,id);
				carts.add(cart);
			}
		return carts;
	}
	
	public void pushCart(RemoteCart cart) throws ConnectionError {
		try {
			cartRemote.add(cart);
			
		} catch (JSONException e) {
			throw new ConnectionError("JSONError: " + e.getMessage());
		}
	}
	
	public void setCartDone(long cartID) throws ConnectionError {
		ContentValues cart = new ContentValues();
		cart.put(POSContract.Cart.COLUMN_NAME_SYNC, 0);
		try {
			Log.d(LOG_TAG,"setCartDone");
			cartLocal.updateCart(cartID, cart);
		} catch (SQLException e) {
			throw new ConnectionError(e.getMessage());
		}
	}

	
	/* ++++++++++++++++++++++++++++++++++++++++++++++++
	 * USER
	 * ++++++++++++++++++++++++++++++++++++++++++++++++
	 */
	
	/**
	 * Used to check a user's login and password.  If the login/password is valid,
	 * then a User object is returned with all of the fields populated.  Any data
	 * passed into the User object is overwritten based on what comes back from the
	 * remote storage.
	 * 
	 * @param user	User object with login and password populated.
	 * @return		Fully populated user object
	 * @throws ConnectionError		A network, protocol, server side, or API error
	 * @throws NoUserFoundException
	 * @throws BadPasswordException
	 */
	public User login(User user) throws ConnectionError, NoUserFoundException, BadPasswordException {
		return user = userRemote.login(user);
	}
	
	/**
	 * Adds a User to the system if the login doesn't already exist.
	 * 
	 * @param user The User to add
	 * @return The same User object with the User ID populated
	 * @throws ConnectionError		A network, protocol, server side, or API error
	 * @throws UserExistsException
	 */
	public User addUser(User user) throws ConnectionError, UserExistsException {
		return userRemote.add(user);
	}
	
	/**
	 * Deletes the user associated with the given login.
	 * 
	 * @param login The login to delete.
	 * @throws ConnectionError		A network, protocol, server side or API error
	 * @throws NoUserFoundException
	 */
	public void deleteUser(String login) throws ConnectionError, NoUserFoundException {
		userRemote.delete(login);
	}
	
	/**
	 * Update a user record.
	 * 
	 * If there is no userID in the User object, then the record is looked up
	 * by the login record.  If there is a userID, then the record to be updated
	 * is looked up by the userID.  This implies that if the login is to be updated,
	 * then the userID needs to be passed in.
	 * 
	 * @param user					User object containing the new values for a user
	 * @throws ConnectionError		A network, protocol, server side, or API error
	 * @throws NoUserFoundException The user record wasn't found.
	 */
	public void updateUser(User user) throws ConnectionError, NoUserFoundException {
		userRemote.update(user);
	}
	
	/**
	 * Gets all of the users of the application including inactive users.
	 * 
	 * @return List of users
	 * @throws ConnectionError		A network, protocol, or API error
	 */
	public List<User> getUsers() throws ConnectionError {
		return userRemote.getAll();
	}
	
	/* ++++++++++++++++++++++++++++++++++++++++++++++++
	 * ITEM
	 * ++++++++++++++++++++++++++++++++++++++++++++++++
	 */
	
	/**
	 * Adds an Item to the inventory. 
	 * 
	 * @param item  Item to add
	 * @throws StorageException
	 */
	public void addItem(Item item) throws StorageException {
		try {
			itemLocal.addItem(item, SyncStatus.PUSH, updUser);
			syncService.push();
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
	}

	/**
	 * Adds a sync'd Item to the inventory. This call is meant to come from
	 * POSRemote.  Needs to get a DONE or we try to push the things we pulled.
	 * 
	 * @param item  Item to add
	 * @throws StorageException
	 */
	public void addSyncdItem(Item item) throws StorageException {
		try {
			Log.i(LOG_TAG, "addSyncdItem " + item.getId());
			itemLocal.addItem(item, SyncStatus.DONE, updUser);
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
	}
	
	/**
	 * Delete the Item from the inventory for the given
	 * item ID.
	 * 
	 * @param itemID  The item ID to delete
	 * @throws StorageException
	 */
	public void deleteItem(String itemID) throws StorageException {
		try {
			itemLocal.delete(itemID);
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
	}
	
	/**
	 * Get all of the items in the inventory.
	 * 
	 * @return
	 * @throws StorageException
	 */
	public List<Item> getAllItems() throws StorageException {
		List<Item> ret = null;
		try {
			ret = itemLocal.getItems();
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
		return ret;
	}

	/**
	 * Get all of the unsync'd items in the inventory.
	 * 
	 * @return
	 * @throws StorageException
	 */
	public List<Item> getUnsyncdItems() throws StorageException {
		List<Item> ret = null;
		try {
			ret = itemLocal.getUnsyncdItems();
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
		return ret;
	}
	
	/**
	 * Get a specific item in the inventory by item ID
	 * 
	 * @param itemID  Item ID to get
	 * @return
	 * @throws StorageException
	 * @throws NoItemFoundException
	 */
	public Item getItem(String itemID) throws StorageException, NoItemFoundException {
		Item ret = null;
		try {
			ret = itemLocal.getItem(itemID);
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
		return ret;
	}
	
	/**
	 * Update item in the inventory.  Updates using the 
	 * Item ID.
	 * 
	 * @param item
	 * @throws StorageException
	 */
	public void updateItem(Item item) throws StorageException {
		try {
			itemLocal.update(item, SyncStatus.PUSH, updUser);
			syncService.push();
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
	}

	/**
	 * Update item in the inventory and set the sync status
	 * 
	 * 
	 * @param item
	 * @throws StorageException
	 */
	public void updateAsSyncdItem(Item item) throws StorageException {
		try {
			itemLocal.update(item, SyncStatus.DONE, updUser);
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
	}
	
	public void syncItem(Item item) throws StorageException {
		try {
			try {
				itemRemote.add(item, updUser);
			} catch (ItemExistsException e) {
				Log.i(LOG_TAG,"Item already exists");
			}
			itemLocal.update(item, SyncStatus.DONE, updUser);
		} catch (ConnectionError e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
	}
	
	/* ++++++++++++++++++++++++++++++++++++++++++++++++
	 * POS Settings
	 * ++++++++++++++++++++++++++++++++++++++++++++++++
	 */
	
	/**
	 * Add the value for the given key to the local
	 * settings.
	 * 
	 * @param key
	 * @param value
	 * @throws StorageException
	 */
	public void addSetting(String key, String value) throws StorageException {
		try {
			settingLocal.add(key, value);
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
	}
	
	/**
	 * Update the local setting value for the given key.
	 * 
	 * @param key	Key to update
	 * @param value
	 * @throws StorageException
	 */
	public void updateSetting(String key, String value) throws StorageException {
		try {
			settingLocal.update(key, value);
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
	}
	
	/**
	 * Get local setting for the given key.
	 * 
	 * @param key The key of the setting
	 * @return
	 * @throws SQLException
	 * @throws StorageException
	 * @throws NoItemFoundException
	 */
	public String getSetting(String key) throws SQLException, StorageException, NoItemFoundException {
		try {
			return settingLocal.get(key);
		} catch (SQLException e) {
			Log.e(LOG_TAG,e.getMessage());
			throw new StorageException(e.getMessage());
		}
	}
	
	/* ++++++++++++++++++++++++++++++++++++++++++++++++
	 * Resend Receipt
	 * ++++++++++++++++++++++++++++++++++++++++++++++++
	 */
	
	/**
	 * Resends the receipt of the last cart for this customer.
	 * 
	 * @param emailAddress  The email address of the customer.
	 * @throws ConnectionError
	 * @throws NoUserFoundException
	 * @throws InvalidCartException
	 */
	public void resendReceipt(String emailAddress) throws ConnectionError, NoUserFoundException, InvalidCartException {
		
	}
	
}

