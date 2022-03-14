package kg.black13.kyrgyzstanwallpaper

import androidx.fragment.app.Fragment

interface FragmentNavigatorAdapterKt {
    fun onCreateFragment(position: Int): Fragment
    fun getTag(position: Int): String
    fun getCount(): Int
}