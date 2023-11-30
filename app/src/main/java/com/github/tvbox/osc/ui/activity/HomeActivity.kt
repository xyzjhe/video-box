package com.github.tvbox.osc.ui.activity

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.IntEvaluator
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.AccelerateInterpolator
import android.view.animation.BounceInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.Keep
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager.widget.ViewPager
import com.github.tvbox.osc.R
import com.github.tvbox.osc.api.ApiConfig
import com.github.tvbox.osc.api.ApiConfig.LoadConfigCallback
import com.github.tvbox.osc.base.BaseActivity
import com.github.tvbox.osc.base.BaseLazyFragment
import com.github.tvbox.osc.bean.AbsSortXml
import com.github.tvbox.osc.bean.SourceBean
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.server.ControlManager
import com.github.tvbox.osc.ui.adapter.HomePageAdapter
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter
import com.github.tvbox.osc.ui.adapter.SortAdapter
import com.github.tvbox.osc.ui.dialog.SelectDialog
import com.github.tvbox.osc.ui.dialog.TipDialog
import com.github.tvbox.osc.ui.fragment.GridFragment
import com.github.tvbox.osc.ui.fragment.GridFragment.Companion.newInstance
import com.github.tvbox.osc.ui.fragment.UserFragment
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer
import com.github.tvbox.osc.ui.tv.widget.FixedSpeedScroller
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager
import com.github.tvbox.osc.ui.tv.widget.ViewObj
import com.github.tvbox.osc.util.*
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.orhanobut.hawk.Hawk
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.TvRecyclerView.OnInBorderKeyEventListener
import com.owen.tvrecyclerview.widget.TvRecyclerView.OnItemListener
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import me.jessyan.autosize.utils.AutoSizeUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.floor

class HomeActivity : BaseActivity() {
    private var currentView: View? = null
    private var topLayout: LinearLayout? = null
    private var contentLayout: LinearLayout? = null
    private var tvName: TextView? = null
    private var tvWifi: ImageView? = null
    private var tvFind: ImageView? = null
    private var tvStyle: ImageView? = null
    private var tvDraw: ImageView? = null
    private var tvMenu: ImageView? = null
    private var tvDate: TextView? = null
    private var mGridView: TvRecyclerView? = null
    private var mViewPager: NoScrollViewPager? = null
    private var sourceViewModel: SourceViewModel? = null
    private var sortAdapter: SortAdapter? = null
    private var pageAdapter: HomePageAdapter? = null
    private val fragments: MutableList<BaseLazyFragment> = ArrayList()
    private var isDownOrUp = false
    private var sortChange = false
    private var currentSelected = 0
    private var sortFocused = 0
    var sortFocusView: View? = null
    private val mHandler = Handler()
    private var mExitTime: Long = 0
    private val mRunnable: Runnable = object : Runnable {
        @SuppressLint("DefaultLocale", "SetTextI18n")
        override fun run() {
            val date = Date()
            @SuppressLint("SimpleDateFormat") val timeFormat = SimpleDateFormat(getString(R.string.hm_date1) + ", " + getString(R.string.hm_date2))
            tvDate!!.text = timeFormat.format(date)
            mHandler.postDelayed(this, 1000)
        }
    }

    override fun getLayoutResID(): Int {
        return R.layout.activity_home
    }

    var useCacheConfig = false
    override fun init() {
        // takagen99: Added to allow read string
        res = getResources()
        EventBus.getDefault().register(this)
        ControlManager.get().startServer()
        initView()
        initViewModel()
        useCacheConfig = false
        val intent = intent
        if (intent != null && intent.extras != null) {
            val bundle = intent.extras
            useCacheConfig = bundle!!.getBoolean("useCache", false)
        }
        initData()
    }

    private fun initView() {
        topLayout = findViewById(R.id.topLayout)
        tvName = findViewById(R.id.tvName)
        tvWifi = findViewById(R.id.tvWifi)
        tvFind = findViewById(R.id.tvFind)
        tvStyle = findViewById(R.id.tvStyle)
        tvDraw = findViewById(R.id.tvDrawer)
        tvMenu = findViewById(R.id.tvMenu)
        tvDate = findViewById(R.id.tvDate)
        contentLayout = findViewById(R.id.contentLayout)
        mGridView = findViewById(R.id.mGridViewCategory)
        mViewPager = findViewById(R.id.mViewPager)
        sortAdapter = SortAdapter()
        mGridView!!.setLayoutManager(V7LinearLayoutManager(mContext, 0, false))
        mGridView!!.setSpacingWithMargins(0, AutoSizeUtils.dp2px(mContext, 10.0f))
        mGridView!!.setAdapter(sortAdapter)
        mGridView!!.setOnItemListener(object : OnItemListener {
            override fun onItemPreSelected(tvRecyclerView: TvRecyclerView, view: View, position: Int) {
                if (!isDownOrUp) {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start()
                    val textView = view.findViewById<TextView>(R.id.tvTitle)
                    textView.paint.isFakeBoldText = false
                    textView.setTextColor(getResources().getColor(R.color.color_FFFFFF_70))
                    textView.invalidate()
                    view.findViewById<View>(R.id.tvFilter).visibility = View.GONE
                }
            }

            override fun onItemSelected(tvRecyclerView: TvRecyclerView, view: View, position: Int) {
                currentView = view
                isDownOrUp = false
                sortChange = true
                view.animate().scaleX(1.1f).scaleY(1.1f).setInterpolator(BounceInterpolator()).setDuration(250).start()
                val textView = view.findViewById<TextView>(R.id.tvTitle)
                textView.paint.isFakeBoldText = true
                textView.setTextColor(getResources().getColor(R.color.color_FFFFFF))
                textView.invalidate()
                //                    if (!sortAdapter.getItem(position).filters.isEmpty())
//                        view.findViewById(R.id.tvFilter).setVisibility(View.VISIBLE);
                val sortData = sortAdapter!!.getItem(position)
                if (sortData!!.filters.isNotEmpty()) {
                    showFilterIcon(sortData.filterSelectCount())
                }
                sortFocusView = view
                sortFocused = position
                mHandler.removeCallbacks(mDataRunnable)
                mHandler.postDelayed(mDataRunnable, 200)
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {
                if (currentSelected == position) {
                    val baseLazyFragment = fragments[currentSelected]
                    if (baseLazyFragment is GridFragment && sortAdapter!!.getItem(position)!!.filters.isNotEmpty()) { // 弹出筛选
                        baseLazyFragment.showFilter()
                    } else if (baseLazyFragment is UserFragment) {
                        showSiteSwitch()
                    }
                }
            }
        })
        mGridView!!.setOnInBorderKeyEventListener(OnInBorderKeyEventListener { direction, _ ->
            if (direction == View.FOCUS_UP) {
                val baseLazyFragment = fragments[sortFocused]
                if (baseLazyFragment is GridFragment) { // 弹出筛选
                    baseLazyFragment.forceRefresh()
                }
            }
            if (direction != View.FOCUS_DOWN) {
                return@OnInBorderKeyEventListener false
            }
            val baseLazyFragment = fragments[sortFocused]
            if (baseLazyFragment !is GridFragment) {
                false
            } else !baseLazyFragment.isLoad()
        })
        // Button : TVBOX >> Delete Cache / Longclick to Refresh Source --
        tvName!!.setOnClickListener { //                dataInitOk = false;
//                jarInitOk = true;
//                showSiteSwitch();
            var dir = cacheDir
            FileUtils.recursiveDelete(dir)
            dir = externalCacheDir
            FileUtils.recursiveDelete(dir)
            Toast.makeText(this@HomeActivity, getString(R.string.hm_cache_del), Toast.LENGTH_SHORT).show()
        }
        tvName!!.setOnLongClickListener {
            reloadHome()
            true
        }
        // Button : Wifi >> Go into Android Wifi Settings -------------
        tvWifi!!.setOnClickListener { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
        // Button : Search --------------------------------------------
        tvFind!!.setOnClickListener { jumpActivity(SearchActivity::class.java) }
        // Button : Style --------------------------------------------
        tvStyle!!.setOnClickListener {
            try {
                Hawk.put(HawkConfig.HOME_REC_STYLE, !Hawk.get(HawkConfig.HOME_REC_STYLE, false))
                if (Hawk.get(HawkConfig.HOME_REC_STYLE, false)) {
                    UserFragment.tvHotListForGrid.visibility = View.VISIBLE
                    UserFragment.tvHotListForLine.visibility = View.GONE
                    Toast.makeText(this@HomeActivity, getString(R.string.hm_style_grid), Toast.LENGTH_SHORT).show()
                    tvStyle!!.setImageResource(R.drawable.hm_up_down)
                } else {
                    UserFragment.tvHotListForGrid.visibility = View.GONE
                    UserFragment.tvHotListForLine.visibility = View.VISIBLE
                    Toast.makeText(this@HomeActivity, getString(R.string.hm_style_line), Toast.LENGTH_SHORT).show()
                    tvStyle!!.setImageResource(R.drawable.hm_left_right)
                }
            } catch (err: Exception) {
                LOG.e(err)
            }
        }
        // Button : Drawer >> To go into App Drawer -------------------
        tvDraw!!.setOnClickListener { jumpActivity(AppsActivity::class.java) }
        // Button : Settings >> To go into Settings --------------------
        tvMenu!!.setOnClickListener { jumpActivity(SettingActivity::class.java) }
        // Button : Settings >> To go into App Settings ----------------
        tvMenu!!.setOnLongClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            true
        }
        // Button : Date >> Go into Android Date Settings --------------
        tvDate!!.setOnClickListener { startActivity(Intent(Settings.ACTION_DATE_SETTINGS)) }
        setLoadSir(contentLayout)
        //mHandler.postDelayed(mFindFocus, 250);
    }

    private fun initViewModel() {
        sourceViewModel = ViewModelProvider(this)[SourceViewModel::class.java]
        sourceViewModel!!.sortResult.observe(this) { absXml ->
            showSuccess()
            if (absXml?.classes != null && absXml.classes.sortList != null) {
                sortAdapter?.setNewData(DefaultConfig.adjustSort(ApiConfig.get()?.homeSourceBean?.key, absXml.classes?.sortList?: listOf(), true))
            } else {
                sortAdapter?.setNewData(DefaultConfig.adjustSort(ApiConfig.get()?.homeSourceBean?.key, ArrayList(), true))
            }
            initViewPager(absXml)
        }
    }

    private var dataInitOk = false
    private var jarInitOk = false

    // takagen99 : Switch to show / hide source title
    private var homeShow = Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false)
    private val isNetworkAvailable: Boolean
        // takagen99 : Check if network is available
        get() {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetworkInfo = cm.activeNetworkInfo
            return activeNetworkInfo != null && activeNetworkInfo.isConnectedOrConnecting
        }

    private fun initData() {

        // takagen99 : Switch to show / hide source title
        val home = ApiConfig.get().homeSourceBean
        if (homeShow) {
            if (home.name != null && home.name.isNotEmpty()) tvName!!.text = home.name
        }

        // takagen99: If network available, check connected Wifi or Lan
        if (isNetworkAvailable) {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            when (cm.activeNetworkInfo!!.type) {
                ConnectivityManager.TYPE_WIFI -> {
                    tvWifi!!.setImageDrawable(res!!.getDrawable(R.drawable.hm_wifi))
                }
                ConnectivityManager.TYPE_MOBILE -> {
                    tvWifi!!.setImageDrawable(res!!.getDrawable(R.drawable.hm_mobile))
                }
                ConnectivityManager.TYPE_ETHERNET -> {
                    tvWifi!!.setImageDrawable(res!!.getDrawable(R.drawable.hm_lan))
                }
            }
        }

        // takagen99: Set Style either Grid or Line
        if (Hawk.get(HawkConfig.HOME_REC_STYLE, false)) {
            tvStyle!!.setImageResource(R.drawable.hm_up_down)
        } else {
            tvStyle!!.setImageResource(R.drawable.hm_left_right)
        }
        mGridView!!.requestFocus()
        if (dataInitOk && jarInitOk) {
            showLoading()
            val key = ApiConfig.get()?.homeSourceBean?.key
            sourceViewModel?.getSort(key)
            if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                LOG.e("有")
            } else {
                LOG.e("无")
            }
            return
        }
        showLoading()
        if (dataInitOk && !jarInitOk) {
            if (ApiConfig.get()?.spider?.isNotEmpty() == true) {
                ApiConfig.get()?.spider?.let {
                    ApiConfig.get()?.loadJar(useCacheConfig, it, object : LoadConfigCallback {
                        override fun success() {
                            jarInitOk = true
                            mHandler.postDelayed({
                                if (!useCacheConfig) {
                                    if (Hawk.get(HawkConfig.HOME_DEFAULT_SHOW, false)) {
                                        jumpActivity(LivePlayActivity::class.java)
                                    }
                                    Toast.makeText(this@HomeActivity, getString(R.string.hm_ok), Toast.LENGTH_SHORT).show()
                                }
                                initData()
                            }, 50)
                        }

                        override fun retry() {}
                        override fun error(msg: String) {
                            jarInitOk = true
                            mHandler.post {
                                if ("" == msg) Toast.makeText(this@HomeActivity, getString(R.string.hm_notok), Toast.LENGTH_SHORT).show() else Toast.makeText(this@HomeActivity, msg, Toast.LENGTH_SHORT).show()
                                initData()
                            }
                        }
                    })
                }
            }
            return
        }
        ApiConfig.get()?.loadConfig(useCacheConfig, object : LoadConfigCallback {
            var dialog: TipDialog? = null
            override fun retry() {
                mHandler.post { initData() }
            }

            override fun success() {
                dataInitOk = true
                if (ApiConfig.get()?.spider?.isEmpty() == true) {
                    jarInitOk = true
                }
                mHandler.postDelayed({ initData() }, 50)
            }

            override fun error(msg: String) {
                if (msg.equals("-1", ignoreCase = true)) {
                    mHandler.post {
                        dataInitOk = true
                        jarInitOk = true
                        initData()
                    }
                    return
                }
                mHandler.post {
                    if (dialog == null) dialog = TipDialog(this@HomeActivity, msg, getString(R.string.hm_retry), getString(R.string.hm_cancel), object : TipDialog.OnListener {
                        override fun left() {
                            mHandler.post {
                                initData()
                                dialog!!.hide()
                            }
                        }

                        override fun right() {
                            dataInitOk = true
                            jarInitOk = true
                            mHandler.post {
                                initData()
                                dialog!!.hide()
                            }
                        }

                        override fun cancel() {
                            dataInitOk = true
                            jarInitOk = true
                            mHandler.post {
                                initData()
                                dialog!!.hide()
                            }
                        }
                    })
                    if (!dialog!!.isShowing) dialog!!.show()
                }
            }
        }, this)
    }

    private fun initViewPager(absXml: AbsSortXml?) {
        if (sortAdapter?.data?.size?.let { it > 0 } == true) {
            for (data in sortAdapter!!.data) {
                if (data.id == "my0") {
                    if (Hawk.get(HawkConfig.HOME_REC, 0) == 1 && absXml != null && absXml.videoList != null && absXml.videoList.size > 0) {
                        fragments.add(UserFragment.newInstance(absXml.videoList))
                    } else {
                        fragments.add(UserFragment.newInstance(null))
                    }
                } else {
                    fragments.add(newInstance(data))
                }
            }
            pageAdapter = HomePageAdapter(supportFragmentManager, fragments)
            try {
                val field = ViewPager::class.java.getDeclaredField("mScroller")
                field.isAccessible = true
                val scroller = FixedSpeedScroller(mContext, AccelerateInterpolator())
                field[mViewPager] = scroller
                scroller.setmDuration(300)
            } catch (e: Exception) {
                LOG.e(e)
            }
            mViewPager!!.setPageTransformer(true, DefaultTransformer())
            mViewPager!!.setAdapter(pageAdapter)
            mViewPager!!.setCurrentItem(currentSelected, false)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {

        // takagen99: Add check for VOD Delete Mode
        if (HawkConfig.hotVodDelete) {
            HawkConfig.hotVodDelete = false
            UserFragment.homeHotVodAdapter.notifyDataSetChanged()
        } else {
            var i = 0
            if (fragments.size <= 0 || sortFocused >= fragments.size || sortFocused.also { i = it } < 0) {
                exit()
                return
            }
            val baseLazyFragment = fragments[i]
            if (baseLazyFragment is GridFragment) {
                val view = sortFocusView
                if (baseLazyFragment.restoreView()) {
                    return
                } // 还原上次保存的UI内容
                if (view != null && !view.isFocused) {
                    sortFocusView!!.requestFocus()
                } else if (sortFocused != 0) {
                    mGridView!!.setSelection(0)
                } else {
                    exit()
                }
            } else if (baseLazyFragment is UserFragment && UserFragment.tvHotListForGrid.canScrollVertically(-1)) {
                UserFragment.tvHotListForGrid.scrollToPosition(0)
                mGridView!!.setSelection(0)
            } else {
                exit()
            }
        }
    }

    private fun exit() {
        if (System.currentTimeMillis() - mExitTime < 2000) {
            //这一段借鉴来自 q群老哥 IDCardWeb
            EventBus.getDefault().unregister(this)
            AppManager.getInstance().appExit(0)
            ControlManager.get().stopServer()
            finish()
            super.onBackPressed()
        } else {
            mExitTime = System.currentTimeMillis()
            Toast.makeText(mContext, getString(R.string.hm_exit), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()

        // takagen99 : Switch to show / hide source title
        val home = ApiConfig.get()?.homeSourceBean
        if (Hawk.get(HawkConfig.HOME_SHOW_SOURCE, false)) {
            if (home != null && home.name != null && home.name.isNotEmpty()) {
                tvName!!.text = home.name
            }
        } else {
            tvName!!.setText(R.string.app_name)
        }

        // takagen99: Icon Placement
        if (Hawk.get(HawkConfig.HOME_SEARCH_POSITION, true)) {
            tvFind!!.visibility = View.VISIBLE
        } else {
            tvFind!!.visibility = View.GONE
        }
        if (Hawk.get(HawkConfig.HOME_MENU_POSITION, true)) {
            tvMenu!!.visibility = View.VISIBLE
        } else {
            tvMenu!!.visibility = View.GONE
        }
        mHandler.post(mRunnable)
    }

    override fun onPause() {
        super.onPause()
        mHandler.removeCallbacksAndMessages(null)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refresh(event: RefreshEvent) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.get()?.getSource("push_agent") != null) {
                val newIntent = Intent(mContext, DetailActivity::class.java)
                newIntent.putExtra("id", event.obj as String)
                newIntent.putExtra("sourceKey", "push_agent")
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                this@HomeActivity.startActivity(newIntent)
            }
        } else if (event.type == RefreshEvent.TYPE_FILTER_CHANGE) {
            if (currentView != null) {
//                showFilterIcon((int) event.obj);
            }
        }
    }

    private fun showFilterIcon(count: Int) {
        val activated = count > 0
        currentView!!.findViewById<View>(R.id.tvFilter).visibility = View.VISIBLE
        val imgView = currentView!!.findViewById<ImageView>(R.id.tvFilter)
        imgView.setColorFilter(if (activated) getThemeColor() else Color.WHITE)
    }

    private val mDataRunnable = Runnable {
        if (sortChange) {
            sortChange = false
            if (sortFocused != currentSelected) {
                currentSelected = sortFocused
                mViewPager!!.setCurrentItem(sortFocused, false)
                changeTop(sortFocused != 0)
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (topHide < 0) return false
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (event.keyCode == KeyEvent.KEYCODE_MENU) {
                showSiteSwitch()
            }
            //            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
//                if () {
//
//                }
//            }
        } else if (event.action == KeyEvent.ACTION_UP) {
        }
        return super.dispatchKeyEvent(event)
    }

    var topHide: Byte = 0
    @Keep
    @SuppressLint("ObjectAnimatorBinding")
    private fun changeTop(hide: Boolean) {
        val viewObj = ViewObj(topLayout, topLayout!!.layoutParams as MarginLayoutParams)
        val animatorSet = AnimatorSet()
        animatorSet.addListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                topHide = (if (hide) 1 else 0).toByte()
            }

            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
        // Hide Top =======================================================
        if (hide && topHide.toInt() == 0) {
            animatorSet.playTogether(ObjectAnimator.ofObject(viewObj, "marginTop", IntEvaluator(), AutoSizeUtils.mm2px(mContext, 20.0f), AutoSizeUtils.mm2px(mContext, 0.0f)),
                    ObjectAnimator.ofObject(viewObj, "height", IntEvaluator(), AutoSizeUtils.mm2px(mContext, 50.0f), AutoSizeUtils.mm2px(mContext, 1.0f)),
                    ObjectAnimator.ofFloat(topLayout, "alpha", 1.0f, 0.0f))
            animatorSet.setDuration(250)
            animatorSet.start()
            tvName!!.isFocusable = false
            tvWifi!!.isFocusable = false
            tvFind!!.isFocusable = false
            tvStyle!!.isFocusable = false
            tvDraw!!.isFocusable = false
            tvMenu!!.isFocusable = false
            return
        }
        // Show Top =======================================================
        if (!hide && topHide.toInt() == 1) {
            animatorSet.playTogether(ObjectAnimator.ofObject(viewObj, "marginTop", IntEvaluator(), AutoSizeUtils.mm2px(mContext, 0.0f), AutoSizeUtils.mm2px(mContext, 20.0f)),
                    ObjectAnimator.ofObject(viewObj, "height", IntEvaluator(), AutoSizeUtils.mm2px(mContext, 1.0f), AutoSizeUtils.mm2px(mContext, 50.0f)),
                    ObjectAnimator.ofFloat(topLayout, "alpha", 0.0f, 1.0f))
            animatorSet.setDuration(250)
            animatorSet.start()
            tvName!!.isFocusable = true
            tvWifi!!.isFocusable = true
            tvFind!!.isFocusable = true
            tvStyle!!.isFocusable = true
            tvDraw!!.isFocusable = true
            tvMenu!!.isFocusable = true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
        AppManager.getInstance().appExit(0)
        ControlManager.get().stopServer()
    }

    // Site Switch on Home Button
    fun showSiteSwitch() {
        val sites: MutableList<SourceBean> = ArrayList()
        for (sb in ApiConfig.get().getSourceBeanList() ?: listOf()) {
            if (sb.hide == 0) sites.add(sb)
        }
        if (sites.size > 0) {
            val dialog = SelectDialog<SourceBean>(this@HomeActivity)

            // Multi Column Selection
            var spanCount = floor((sites.size / 10).toDouble()).toInt()
            if (spanCount <= 1) spanCount = 1
            if (spanCount >= 3) spanCount = 3
            val tvRecyclerView = dialog.findViewById<TvRecyclerView>(R.id.list)
            tvRecyclerView.setLayoutManager(V7GridLayoutManager(dialog.context, spanCount))
            val clRoot = dialog.findViewById<LinearLayout>(R.id.cl_root)
            val clp = clRoot.layoutParams
            if (spanCount != 1) {
                clp.width = AutoSizeUtils.mm2px(dialog.context, (400 + 260 * (spanCount - 1)).toFloat())
            }
            dialog.setTip(getString(R.string.dia_source))
            dialog.setAdapter(object : SelectDialogAdapter.SelectDialogInterface<SourceBean?> {
                override fun click(value: SourceBean?, pos: Int) {
                    ApiConfig.get()?.setSourceBean(value)
                    reloadHome()
                }

                override fun getDisplay(`val`: SourceBean?): String? {
                    return `val`?.name
                }
            }, object : DiffUtil.ItemCallback<SourceBean>() {
                override fun areItemsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                    return oldItem === newItem
                }

                override fun areContentsTheSame(oldItem: SourceBean, newItem: SourceBean): Boolean {
                    return oldItem.key == newItem.key
                }
            }, sites, sites.indexOf(ApiConfig.get().homeSourceBean))
            dialog.setOnDismissListener {
                //                    if (homeSourceKey != null && !homeSourceKey.equals(Hawk.get(HawkConfig.HOME_API, ""))) {
//                        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
//                        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
//                        Bundle bundle = new Bundle();
//                        bundle.putBoolean("useCache", true);
//                        intent.putExtras(bundle);
//                        HomeActivity.this.startActivity(intent);
//                    }
            }
            dialog.show()
        }
    }

    fun reloadHome() {
        val intent = Intent(applicationContext, HomeActivity::class.java)
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val bundle = Bundle()
        bundle.putBoolean("useCache", true)
        intent.putExtras(bundle)
        this@HomeActivity.startActivity(intent)
    } //    public void onClick(View v) {

    //        FastClickCheckUtil.check(v);
    //        if (v.getId() == R.id.tvFind) {
    //            jumpActivity(SearchActivity.class);
    //        } else if (v.getId() == R.id.tvMenu) {
    //            jumpActivity(SettingActivity.class);
    //        }
    //    }
    companion object {
        // takagen99: Added to allow read string
        // takagen99: Added to allow read string
        @JvmStatic
        var res: Resources? = null
            private set
    }
}