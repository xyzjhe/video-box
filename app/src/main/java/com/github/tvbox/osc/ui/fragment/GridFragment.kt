package com.github.tvbox.osc.ui.fragment

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.BounceInterpolator
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import com.chad.library.adapter.base.BaseQuickAdapter
import com.github.tvbox.osc.R
import com.github.tvbox.osc.base.BaseLazyFragment
import com.github.tvbox.osc.bean.MovieSort.SortData
import com.github.tvbox.osc.event.RefreshEvent
import com.github.tvbox.osc.ui.activity.DetailActivity
import com.github.tvbox.osc.ui.activity.FastSearchActivity
import com.github.tvbox.osc.ui.adapter.GridAdapter
import com.github.tvbox.osc.ui.dialog.GridFilterDialog
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView
import com.github.tvbox.osc.util.FastClickCheckUtil
import com.github.tvbox.osc.viewmodel.SourceViewModel
import com.owen.tvrecyclerview.widget.TvRecyclerView
import com.owen.tvrecyclerview.widget.TvRecyclerView.OnItemListener
import com.owen.tvrecyclerview.widget.V7GridLayoutManager
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager
import org.greenrobot.eventbus.EventBus
import java.util.*

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
class GridFragment : BaseLazyFragment() {
    private var sortData: SortData? = null
    private var mGridView: TvRecyclerView? = null
    private var sourceViewModel: SourceViewModel? = null
    private var gridFilterDialog: GridFilterDialog? = null
    private var gridAdapter: GridAdapter? = null
    private var page = 1
    private var maxPage = 1
    private var isLoad = false
    var isTop = true
        private set
    private var focusedView: View? = null
    override val layoutResID: Int
        get() = R.layout.fragment_grid

    inner class GridInfo {
        var sortID = ""
        var mGridView: TvRecyclerView? = null
        var gridAdapter: GridAdapter? = null
        var page = 1
        var maxPage = 1
        var isLoad = false
        var focusedView: View? = null
    }

    var mGrids = Stack<GridInfo>() //ui栈
    fun setArguments(sortData: SortData): GridFragment {
        this.sortData = sortData
        return this
    }

    override fun init() {
        initView()
        initViewModel()
        initData()
    }

    private fun changeView(id: String) {
        initView()
        sortData?.id = id // 修改sortData.id为新的ID
        initViewModel()
        initData()
    }

    val isFolederMode: Boolean
        get() = uITag == '1'
    val uITag: Char
        // 获取当前页面UI的显示模式 ‘0’ 正常模式 '1' 文件夹模式 '2' 显示缩略图的文件夹模式
        get() {
            return if (sortData?.flag == null || sortData?.flag?.length == 0) '0' else sortData?.flag?.get(0)?:'0'
        }

    // 是否允许聚合搜索 sortData.flag的第二个字符为‘1’时允许聚搜
    fun enableFastSearch(): Boolean {
        return if (sortData?.flag == null || sortData?.flag?.length.let { it!! < 2 }) false else sortData?.flag?.get(1)?.equals('1')?:true
    }

    // 保存当前页面
    private fun saveCurrentView() {
        if (mGridView == null) return
        val info = GridInfo()
        info.sortID = sortData?.id.toString()
        info.mGridView = mGridView
        info.gridAdapter = gridAdapter
        info.page = page
        info.maxPage = maxPage
        info.isLoad = isLoad
        info.focusedView = focusedView
        mGrids.push(info)
    }

    // 丢弃当前页面，将页面还原成上一个保存的页面
    fun restoreView(): Boolean {
        if (mGrids.empty()) return false
        showSuccess()
        mGridView?.parent?.let { (it as ViewGroup).removeView(mGridView)  } // 重父窗口移除当前控件
        val info = mGrids.pop() // 还原上次保存的控件
        sortData?.id = info.sortID
        mGridView = info.mGridView
        gridAdapter = info.gridAdapter
        page = info.page
        maxPage = info.maxPage
        isLoad = info.isLoad
        focusedView = info.focusedView
        mGridView!!.visibility = View.VISIBLE
        //        if(this.focusedView != null){ this.focusedView.requestFocus(); }
        if (mGridView != null) mGridView!!.requestFocus()
        return true
    }

    // 更改当前页面
    private fun createView() {
        saveCurrentView() // 保存当前页面
        if (mGridView == null) { // 从layout中拿view
            mGridView = findViewById(R.id.mGridView)
        } else { // 复制当前view
            val v3 = TvRecyclerView(mContext)
            v3.setSpacingWithMargins(10, 10)
            v3.layoutParams = mGridView!!.layoutParams
            v3.setPadding(mGridView!!.paddingLeft, mGridView!!.paddingTop, mGridView!!.paddingRight, mGridView!!.paddingBottom)
            v3.setClipToPadding(mGridView!!.clipToPadding)
            (mGridView!!.parent as ViewGroup).addView(v3)
            mGridView!!.visibility = View.GONE
            mGridView = v3
            mGridView!!.visibility = View.VISIBLE
        }
        mGridView!!.setHasFixedSize(true)
        gridAdapter = GridAdapter(isFolederMode)
        page = 1
        maxPage = 1
        isLoad = false
    }

    private fun initView() {
        createView()
        mGridView!!.setAdapter(gridAdapter)
        if (isFolederMode) {
            mGridView!!.setLayoutManager(V7LinearLayoutManager(mContext, 1, false))
        } else {
            mGridView!!.setLayoutManager(V7GridLayoutManager(mContext, if (isBaseOnWidth()) 5 else 6))
        }
        gridAdapter!!.setOnLoadMoreListener({
            gridAdapter!!.setEnableLoadMore(true)
            sourceViewModel!!.getList(sortData, page)
        }, mGridView)
        mGridView!!.setOnItemListener(object : OnItemListener {
            override fun onItemPreSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(BounceInterpolator()).start()
            }

            override fun onItemSelected(parent: TvRecyclerView, itemView: View, position: Int) {
                itemView.animate().scaleX(1.2f).scaleY(1.2f).setDuration(300).setInterpolator(BounceInterpolator()).start()
            }

            override fun onItemClick(parent: TvRecyclerView, itemView: View, position: Int) {}
        })
        mGridView!!.setOnInBorderKeyEventListener { direction, _ ->
            false
        }
        gridAdapter!!.setOnItemClickListener { _, view, position ->
            FastClickCheckUtil.check(view)
            val video = gridAdapter!!.data[position]
            if (video != null) {
                val bundle = Bundle()
                bundle.putString("id", video.id)
                bundle.putString("sourceKey", video.sourceKey)
                bundle.putString("title", video.name)
                if ("12".indexOf(uITag) != -1 && video.tag == "folder") {
                    focusedView = view
                    changeView(video.id)
                } else {
                    if (video.id == null || video.id.isEmpty() || video.id.startsWith("msearch:")) {
                        jumpActivity(FastSearchActivity::class.java, bundle)
                    } else {
                        jumpActivity(DetailActivity::class.java, bundle)
                    }
                }
            }
        }
        // takagen99 : Long Press to Fast Search
        gridAdapter!!.onItemLongClickListener = BaseQuickAdapter.OnItemLongClickListener { adapter, view, position ->
            FastClickCheckUtil.check(view)
            val video = gridAdapter!!.data[position]
            if (video != null) {
                val bundle = Bundle()
                bundle.putString("id", video.id)
                bundle.putString("sourceKey", video.sourceKey)
                bundle.putString("title", video.name)
                jumpActivity(FastSearchActivity::class.java, bundle)
            }
            true
        }
        gridAdapter!!.setLoadMoreView(LoadMoreView())
        setLoadSir(mGridView)
    }

    private fun initViewModel() {
        if (sourceViewModel != null) {
            return
        }
        sourceViewModel = ViewModelProvider(this).get(SourceViewModel::class.java)
        sourceViewModel!!.listResult.observe(this) { absXml -> //                if(mGridView != null) mGridView.requestFocus();
            if (absXml?.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size > 0) {
                if (page == 1) {
                    showSuccess()
                    isLoad = true
                    gridAdapter!!.setNewData(absXml.movie.videoList)
                } else {
                    gridAdapter!!.addData(absXml.movie.videoList)
                }
                page++
                maxPage = absXml.movie.pagecount
                if (page > maxPage) {
                    gridAdapter!!.loadMoreEnd()
                    gridAdapter!!.setEnableLoadMore(false)
                } else {
                    gridAdapter!!.loadMoreComplete()
                    gridAdapter!!.setEnableLoadMore(true)
                }
            } else {
                if (page == 1) {
                    showEmpty()
                }
                if (page > maxPage) {
                    Toast.makeText(context, "没有更多了", Toast.LENGTH_SHORT).show()
                    gridAdapter!!.loadMoreEnd()
                } else {
                    gridAdapter!!.loadMoreComplete()
                }
                gridAdapter!!.setEnableLoadMore(false)
            }
        }
    }

    fun isLoad(): Boolean {
        return isLoad || !mGrids.empty() //如果有缓存页的话也可以认为是加载了数据的
    }

    private fun initData() {
        showLoading()
        isLoad = false
        scrollTop()
        toggleFilterStatus()
        sourceViewModel!!.getList(sortData, page)
    }

    private fun toggleFilterStatus() {
        if (sortData?.filters?.isNotEmpty() == true) {
            val count = sortData?.filterSelectCount()
            EventBus.getDefault().post(RefreshEvent(RefreshEvent.TYPE_FILTER_CHANGE, count))
        }
    }

    fun scrollTop() {
        isTop = true
        mGridView!!.scrollToPosition(0)
    }

    fun showFilter() {
        if (sortData?.filters?.isNotEmpty() == true && gridFilterDialog == null) {
            gridFilterDialog = mContext?.let { GridFilterDialog(it) }
            gridFilterDialog!!.setData(sortData)
            gridFilterDialog!!.setOnDismiss {
                page = 1
                initData()
            }
        }
        if (gridFilterDialog != null) gridFilterDialog!!.show()
    }

    fun forceRefresh() {
        page = 1
        initData()
    }

    companion object {
        @JvmStatic
        fun newInstance(sortData: SortData): GridFragment {
            return GridFragment().setArguments(sortData)
        }
    }
}