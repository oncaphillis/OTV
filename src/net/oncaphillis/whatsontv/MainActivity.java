package net.oncaphillis.whatsontv;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.oncaphillis.whatsontv.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.support.v4.app.Fragment;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.support.v4.app.ActionBarDrawerToggle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ExpandableListView.OnGroupClickListener;
import info.movito.themoviedbapi.*;
import info.movito.themoviedbapi.model.tv.*;



public class MainActivity extends FragmentActivity {
	
	private ViewPager _viewPager = null;

	private MainPagerAdapter _mainPagerAdapter = null;
	
	public static final String[] Titles={"Today","On the Air","Hi Vote","Popular" };

	static public ArrayAdapter<TvSeries>[]          ListAdapters = new ArrayAdapter[Titles.length];
	
	static public Map<Integer,List<TvSeries>>[] StoredResults = new HashMap[Titles.length];
	
	//static public Integer[] Counts = new Integer[Titles.length];
	
	static private List<TvSeries>[] MainList = new List[Titles.length];
	
	static private Pager[] ThePager = new Pager[Titles.length];
	
	static private Bitmap _defBitmap = null;

	public  SearchThread SearchThread = null;
	private ActionBarDrawerToggle _DrawerToggle = null;
	private DrawerLayout          _DrawerLayout = null; 
	private ExpandableListView    _DrawerList   = null;

	public SharedPreferences Preferences;
	
	class SeriesStorage extends HashMap<Integer,List<TvSeries>> {
		@Override
		public List<TvSeries> put(Integer key, List<TvSeries> value) {
			return super.put(key,value);
		}
		@Override
		public List<TvSeries> remove(Object key) {
			return super.remove(key);
		}
		@Override
		public void clear() {
			super.clear();
		}
	};
	
	abstract class CachingPager extends Pager {
		
		private Map<Integer,List<TvSeries>> _storage;
		private int _totalCount = -1;
		
		abstract public TvResultsPage request(int page);
		
		public CachingPager(Map<Integer,List<TvSeries>> storage) {
			_storage = storage;
		}

		public List<TvSeries> getPage(int page) {

			List<TvSeries> series_list;
				
			synchronized( _storage  )  {					
				if( ( series_list = _storage.get(page))!=null)
					return series_list;
			}	
			
			while(true) {
				if(!isOnline()) {
					try {
						Thread.sleep(1000);
					} catch(Exception ex1) {
					}
					continue;
				}
				
				try {
					TvResultsPage r =  request(page);
					series_list = r.getResults();
					_totalCount = r.getTotalResults();
					
					if(series_list!=null) {
						synchronized(_storage )  {					
							_storage.put(page, series_list);
							return series_list;
						}
					}	
				} catch(Exception ex0) {
					try {
						Thread.sleep(1000);
						String ss = ex0.getMessage();
					} catch(Exception ex1) {
					}
				}
			}
		}
		
		public void start() {
		}

		public void end() {
		}
		
		public int getTotal() {
			return _totalCount;
		}
	}

	private void checkOnline() { 
		if(!isOnline()) {
			final MainActivity a=this;
			new AlertDialog.Builder(this)
			.setTitle("Alert")
			.setMessage("We are not online")
			.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int which) { 
					a.checkOnline();
				}
			})
			.setIcon(android.R.drawable.ic_dialog_alert).show();
		}

	}
	
	/** main activity after startup
	 */
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main_pager);

		Preferences = getPreferences(MODE_PRIVATE);
		
		initNavbar();
		final Activity act = this; 

		try {			
			_defBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.no_image);

			_mainPagerAdapter = new MainPagerAdapter(getSupportFragmentManager(),this);
			
			_viewPager = (ViewPager) findViewById(R.id.main_pager_layout);
	        _viewPager.setAdapter(_mainPagerAdapter);
	        _viewPager.setCurrentItem(0);
	        
	        checkOnline();
	        
	        for(int i=0;i< Titles.length ;i++) {
				final int j = i;
	        	if(StoredResults[i]==null)
					StoredResults[i] = new SeriesStorage();
	
	        	if(MainList[i]==null)
					MainList[i] = new ArrayList<TvSeries>();
				
	        	if(ListAdapters[i]==null)
					ListAdapters[i] = new TvSeriesListAdapter(this,
						android.R.layout.simple_list_item_1,MainList[i],_defBitmap,this);
	        	
	        	final int idx = i;
	        	if(ThePager[i]==null) {
	        		ThePager[i] = new CachingPager(StoredResults[i]) {
						public TvResultsPage request(int page) {
			        		switch(idx) {
			        		case 0:
		        				return api().getTvSeries().getAiringToday(Tmdb.getLanguage(), page,Tmdb.getTimezone());

			        		case 1:
		        				return api().getTvSeries().getOnTheAir(Tmdb.getLanguage(),page);

			        		case 2:	
		        				return api().getTvSeries().getTopRated(Tmdb.getLanguage(),page);

			        		default:
		        				return api().getTvSeries().getPopular(Tmdb.getLanguage(), page);
			        		}
						}

					};
	        	}
	        }

	        SearchThread = new SearchThread(this,ListAdapters,ThePager,null,null);
		        
		    final FragmentActivity a = this; 
		        
	        Thread.UncaughtExceptionHandler h = new Thread.UncaughtExceptionHandler() {
	            public void uncaughtException(Thread th, Throwable ex) {
	            	
	            	Bundle b        = new Bundle();
	            	
	            	if(ex.getMessage()!=null)
	            		b.putString("txt1", ex.getMessage());
	            	else
	            		b.putString("txr1", " ? ? ? ");
	            	
	            	if(ex.getCause()!=null && ex.getCause().getMessage()!=null)
	            		b.putString("txt2", ex.getCause().getMessage());
	            	else
	            		b.putString("txt2", "...");
	            		
	    			Intent myIntent = new Intent(a, ErrorActivity.class);
	    			myIntent.putExtras(b);
	    			startActivity(myIntent);
	    			finish();
	            }
	        };
	        
	        SearchThread.setUncaughtExceptionHandler(h);
	        SearchThread.start();
	        
		} catch(Exception ex) {
			Intent myIntent = new Intent(this, ErrorActivity.class);
			Bundle b        = new Bundle();
			b.putString("txt", ex.getMessage()+" "+ex.getCause());
			myIntent.putExtras(b);
			startActivity(myIntent);
		} catch(Throwable ta) {
			Intent myIntent = new Intent(this,ErrorActivity.class);
			Bundle b = new Bundle();
			b.putString("txt", ta.getMessage()+" "+ta.getCause());
			myIntent.putExtras(b);
			startActivity(myIntent);
		}
	}

	@Override
	public void onAttachFragment(Fragment fragment) {
		super.onAttachFragment(fragment);
	}
	  
	private void initNavbar() {
        _DrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        _DrawerList  = (ExpandableListView) findViewById(R.id.left_drawer);
        _DrawerList.setGroupIndicator(null);
        
        // Set the adapter for the list view
        
        _DrawerList.setAdapter( new NavigatorAdapter(this)) ;
        final MainActivity activity = this;
        // Set the list's click listener
        _DrawerList.setOnGroupClickListener( new OnGroupClickListener() {
			@Override
			public boolean onGroupClick(ExpandableListView parent, View v,
					int groupPosition, long id) {
				if(groupPosition==NavigatorAdapter.ABOUT) {
					Intent myIntent = new Intent( activity, AboutActivity.class);
					Bundle b        = new Bundle();
			        _DrawerLayout.closeDrawers();
					startActivity(myIntent);
					return true;
				} else if(groupPosition==NavigatorAdapter.SETUP) {
					Intent myIntent = new Intent( activity, SetupActivity.class);
					Bundle b        = new Bundle();
			        _DrawerLayout.closeDrawers();
					startActivity(myIntent);
					return true;
				} else if(groupPosition==NavigatorAdapter.LOGIN) {
					Intent myIntent = new Intent( activity, LoginActivity.class);
					Bundle b        = new Bundle();
			        _DrawerLayout.closeDrawers();
					startActivity(myIntent);
					return true;
				}				
				return false;
			}
        });

        _DrawerList.setOnChildClickListener(new OnChildClickListener() {
			@Override
			public boolean onChildClick(ExpandableListView parent, View v,
					int groupPosition, int childPosition, long id) {
				if(groupPosition == NavigatorAdapter.LISTS) {
			        _DrawerLayout.closeDrawers();
					activity._viewPager.setCurrentItem(childPosition);
					return true;
				}
				return false;
			}
        	
        });
        
        getActionBar().setDisplayHomeAsUpEnabled(true);
	    getActionBar().setHomeButtonEnabled(true);
	     
		 _DrawerToggle = new ActionBarDrawerToggle(
	            this,                   /* host Activity */
	            _DrawerLayout,          /* DrawerLayout object */
	            R.drawable.ic_drawer,   /* nav drawer image to replace 'Up' caret */
	            R.string.nav_bar_open,  /* "open drawer" description for accessibility */
	            R.string.nav_bar_close  /* "close drawer" description for accessibility */
	    ) {
	        @Override
	        public void onDrawerClosed(View drawerView) {
	        	super.onDrawerClosed(drawerView);
	            invalidateOptionsMenu();
	        }

	        @Override
	        public void onDrawerOpened(View drawerView) {
	        	super.onDrawerOpened(drawerView);
	            invalidateOptionsMenu();
	        }
	    };
	    
	    _DrawerLayout.post(new Runnable() {
	        @Override
	        public void run() {
	            _DrawerToggle.syncState();
	        }
	    });
	    _DrawerLayout.setDrawerListener(_DrawerToggle);
	}

	protected void onDestroy() {
		super.onDestroy();
	}
	
	@Override 
	protected void 
	onStart() {
		super.onStart();
	}
	@Override
	protected void	onPause() {
		if(SearchThread!=null)
			SearchThread.lock();
		super.onPause();
	}
	
	@Override
	protected void	onResume() {
		if(SearchThread!=null)
			SearchThread.release();
		super.onResume();
	}
	
	@Override
	protected void onStop() {
		super.onStop();
	}
	
	@Override
	public boolean onMenuItemSelected(int feature,MenuItem it) {
		return super.onMenuItemSelected(feature, it);
	}
	
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // Sync the toggle state after onRestoreInstanceState has occurred.
        _DrawerToggle.syncState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        _DrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Pass the event to ActionBarDrawerToggle, if it returns
        // true, then it has handled the app icon touch event
        if (_DrawerToggle.onOptionsItemSelected(item)) {
          return true;
        }
        // Handle your other action bar items...

        return super.onOptionsItemSelected(item);
    }

	@Override
	protected void  onSaveInstanceState (Bundle outState){
		synchronized(StoredResults) {
			outState.putSerializable("savedstate", StoredResults);
		}
		super.onSaveInstanceState (outState);
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    // Inflate the menu items for use in the action bar
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.main, menu);
	    
	    SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
	    SearchView searchView       = (SearchView) menu.findItem(R.id.search).getActionView();
	    
	    searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
	    searchView.setIconifiedByDefault(true);

	    return super.onCreateOptionsMenu(menu);
	}

	public boolean isOnline() {
	    ConnectivityManager cm =
	        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo netInfo = cm.getActiveNetworkInfo();
	    return netInfo != null && netInfo.isConnectedOrConnecting();
	}
}
