package kg.black13.kyrgyzstanwallpaper

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener

class ContentFragmentKt : Fragment() {
    companion object {
        private const val ARG_TYPE = "type"
        // Сколько рядов картинок между рекламными блоками
        private const val ROWS_BETWEEN_ADS = 4

        fun newInstance(type: String): ContentFragmentKt {
            val fragment = ContentFragmentKt()
            val args = Bundle()
            args.putString(ARG_TYPE, type)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.grid_fragment, container, false)
        val contentType = requireArguments().getString(ARG_TYPE)!!
        val manager = ManagerKt.getInstance()!!
        val list = view.findViewById<RecyclerView>(R.id.content_list)
        manager.pgsBar = view.findViewById(R.id.pBar)
        manager.pgsBar!!.visibility = View.VISIBLE
        val emptyText = view.findViewById<TextView>(R.id.emptyText)
        emptyText.setText(
            when (contentType) {
                FavoritesKt.TYPE -> R.string.favorites_empty
                CatalogKt.CARDS -> R.string.cards_empty
                else -> R.string.load_failed
            }
        )
        manager.emptyView = emptyText
        manager.pullToRefresh = view.findViewById(R.id.pullToRefresh)

        // На планшетах три колонки
        val columns = if (resources.configuration.smallestScreenWidthDp >= 600) 3 else 2
        val adapter = PhotoAdapterKt(manager.paginationList, columns * ROWS_BETWEEN_ADS) { position ->
            (activity as MainActivityKt).openGallery(position)
        }
        val layoutManager = GridLayoutManager(view.context, columns)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return if (adapter.isAd(position)) columns else 1
            }
        }
        list.layoutManager = layoutManager
        list.adapter = adapter
        manager.arrayAdapter = adapter

        manager.pullToRefresh!!.setOnRefreshListener(OnRefreshListener {
            // Избранное и лента хранятся на устройстве, им сеть не обязательна
            if (contentType != FavoritesKt.TYPE && contentType != CatalogKt.FEED && !manager.isOnline()) {
                Toast.makeText(view.context, R.string.no_internet, Toast.LENGTH_SHORT).show()
                manager.pullToRefresh!!.isRefreshing = false
                return@OnRefreshListener
            }
            manager.paginationList.clear()
            manager.loadFirstItems(contentType)
        })

        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                // Подгружаем следующую страницу незадолго до конца списка
                if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - columns * 2) {
                    manager.loadNextItems()
                }
            }
        })

        manager.loadFirstItems(contentType)
        return view
    }
}
