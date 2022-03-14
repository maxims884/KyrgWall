package kg.black13.kyrgyzstanwallpaper

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.GridView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener

class ContentFragmentKt(type: String) : Fragment() {
    private var isLoadAddItems = true
    private var contentType = "nature"
    init {
        contentType = type
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.grid_fragment, container, false)
        val gridViewData = view.findViewById<GridView>(R.id.content_list)
        ManagerKt.getInstance()?.pgsBar = view.findViewById(R.id.pBar)
        ManagerKt.getInstance()?.pgsBar!!.visibility = View.VISIBLE
        ManagerKt.getInstance()?.pullToRefresh = view.findViewById(R.id.pullToRefresh)
        gridViewData.numColumns = 2
        gridViewData.horizontalSpacing = 10
        gridViewData.verticalSpacing = 10
        gridViewData.isNestedScrollingEnabled = true
        ManagerKt.getInstance()?.pullToRefresh!!.setOnRefreshListener(OnRefreshListener {
            ManagerKt.getInstance()?.pullToRefresh!!.isRefreshing = true
            if (!ManagerKt.getInstance()?.isOnline()!!) {
                Toast.makeText(view.context, "Нет подключения к интернету", Toast.LENGTH_SHORT)
                    .show()
                ManagerKt.getInstance()?.pullToRefresh!!.isRefreshing = false
                return@OnRefreshListener
            }
            ManagerKt.getInstance()?.paginationList?.clear()
            ManagerKt.getInstance()?.loadFirstItems(contentType)
        })
        if (ContextCompat.checkSelfPermission(view.context, Manifest.permission.INTERNET)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted
            return view
        }

        ManagerKt.getInstance()?.arrayAdapter =
            PhotoAdapterKt( ManagerKt.getInstance()?.paginationList, ManagerKt.getInstance()?.pgsBar!!)
        gridViewData.adapter =  ManagerKt.getInstance()?.arrayAdapter

        ManagerKt.getInstance()?.loadFirstItems(contentType)
        gridViewData.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScroll(
                view: AbsListView,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                if (firstVisibleItem + visibleItemCount >= totalItemCount && totalItemCount > 0 && isLoadAddItems) {
                    // End has been reached
                    ManagerKt.getInstance()?.loadNextItems()
                    isLoadAddItems = false
                }
                if (firstVisibleItem + visibleItemCount < totalItemCount && !isLoadAddItems) {
                    isLoadAddItems = true
                }
            }

            override fun onScrollStateChanged(view: AbsListView, scrollState: Int) {}
        })
        return view
    }
}