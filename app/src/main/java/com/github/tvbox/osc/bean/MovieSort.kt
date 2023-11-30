package com.github.tvbox.osc.bean

import com.thoughtworks.xstream.annotations.XStreamAlias
import com.thoughtworks.xstream.annotations.XStreamAsAttribute
import com.thoughtworks.xstream.annotations.XStreamConverter
import com.thoughtworks.xstream.annotations.XStreamImplicit
import com.thoughtworks.xstream.converters.extended.ToAttributedValueConverter
import java.io.Serializable

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
@XStreamAlias("class")
class MovieSort : Serializable {
    @JvmField
    @XStreamImplicit(itemFieldName = "ty")
    var sortList: List<SortData>? = null

    @XStreamAlias("ty")
    @XStreamConverter(value = ToAttributedValueConverter::class, strings = ["name"])
    class SortData : Serializable, Comparable<SortData> {
        @JvmField
        @XStreamAsAttribute
        var id: String? = null
        @JvmField
        var name: String? = null
        var sort = -1
        var select = false
        @JvmField
        var filters = ArrayList<SortFilter>()
        @JvmField
        var filterSelect: HashMap<String, String>? = HashMap()
        @JvmField
        var flag: String? = null // 类型

        constructor()
        constructor(id: String?, name: String?) {
            this.id = id
            this.name = name
        }

        fun filterSelectCount(): Int {
            if (filterSelect == null) {
                return 0
            }
            var count = 0
            for (filter in filterSelect!!.values) {
                if (filter != null) {
                    count++
                }
            }
            return count
        }

        override fun compareTo(o: SortData): Int {
            return sort - o.sort
        }
    }

    class SortFilter {
        @JvmField
        var key: String? = null
        @JvmField
        var name: String? = null
        @JvmField
        var values: LinkedHashMap<String, String>? = null
    }
}