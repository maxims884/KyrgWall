package kg.black13.kyrgyzstanwallpaper

import androidx.fragment.app.Fragment

class FragmentAdapterKt : FragmentNavigatorAdapterKt {
    override fun onCreateFragment(position: Int): Fragment {
        when (position) {
            FragmentIDsKt.NatureFragment.value -> return ContentFragmentKt("nature")
            FragmentIDsKt.AnimalsFragment.value -> return ContentFragmentKt("animals")
            FragmentIDsKt.Gallery.value -> return GalleryKt()
            FragmentIDsKt.ArchFragment.value -> return ContentFragmentKt("arch")
            FragmentIDsKt.ReligionFragment.value -> return ContentFragmentKt("relig")
            FragmentIDsKt.StarsFragment.value -> return ContentFragmentKt("stars")
            FragmentIDsKt.Settings.value -> return SettingsKt()
        }
        return ContentFragmentKt("nature")
    }

    override fun getTag(position: Int): String {
        when (position) {
            FragmentIDsKt.NatureFragment.value -> return "nature"
            FragmentIDsKt.AnimalsFragment.value -> return "animals"
            FragmentIDsKt.Gallery.value -> return GalleryKt::class.java.simpleName
            FragmentIDsKt.ArchFragment.value -> return "arch"
            FragmentIDsKt.ReligionFragment.value -> return "relig"
            FragmentIDsKt.StarsFragment.value -> return "stars"
            FragmentIDsKt.Settings.value -> return SettingsKt::class.java.kotlin.simpleName!!
        }
        return "nature"
    }

    override fun getCount(): Int {
        return 7
    }
}