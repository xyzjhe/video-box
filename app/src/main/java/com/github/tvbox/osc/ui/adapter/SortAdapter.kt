package com.github.tvbox.osc.ui.adapter

import com.chad.library.adapter.base.BaseQuickAdapter
import com.chad.library.adapter.base.BaseViewHolder
import com.github.tvbox.osc.R
import com.github.tvbox.osc.bean.MovieSort.SortData

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
class SortAdapter : BaseQuickAdapter<SortData, BaseViewHolder>(R.layout.item_home_sort, ArrayList()) {
    override fun convert(helper: BaseViewHolder, item: SortData) {
        helper.setText(R.id.tvTitle, item.name)
    }
}