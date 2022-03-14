package kg.black13.kyrgyzstanwallpaper

import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

class FragmentNavigatorKt(
    fragmentManager: FragmentManager,
    adapter: FragmentNavigatorAdapterKt,
    @IdRes containerViewId: Int
) {
    private val EXTRA_CURRENT_POSITION = "extra_current_position"

    private var mFragmentManager: FragmentManager? = null

    private var mAdapter: FragmentNavigatorAdapterKt? = null

    @IdRes
    private var mContainerViewId = 0

    private var mCurrentPosition = -1

    private var mDefaultPosition = 0
    init{
        mFragmentManager = fragmentManager
        mAdapter = adapter
        mContainerViewId = containerViewId
    }

    fun onCreate(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            mCurrentPosition = savedInstanceState.getInt(
                EXTRA_CURRENT_POSITION,
                mDefaultPosition
            )
        }
    }
    fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(EXTRA_CURRENT_POSITION, mCurrentPosition)
    }

    fun clearBackStack() {
        for (i in 0 until mFragmentManager!!.backStackEntryCount) {
            mFragmentManager!!.popBackStack()
        }
    }

    fun showFragmentStack(position: Int) {
        mCurrentPosition = position
        val transaction = mFragmentManager!!.beginTransaction()
        //        int count = mAdapter.getCount();
        show(position, transaction)
        //        for (int i = 0; i < count; i++) {
//            if (position == i) {
//                show(i, transaction);
//            } else {
//                hide(i, transaction);
//            }
//        }
        transaction.addToBackStack(null)
        transaction.commit()
    }

    /**
     * @see .showFragment
     */
    fun showFragment(position: Int) {
        showFragment(position, false)
    }

    /**
     * @see .showFragment
     */
    fun showFragment(position: Int, reset: Boolean) {
        showFragment(position, reset, false)
    }

    /**
     * Show fragment at given position
     *
     * @param position          fragment position
     * @param reset             true if fragment in given position need reset otherwise false
     * @param allowingStateLoss true if allowing state loss otherwise false
     */
    fun showFragment(position: Int, reset: Boolean, allowingStateLoss: Boolean) {
        mCurrentPosition = position
        val transaction = mFragmentManager!!.beginTransaction()
        val count = mAdapter!!.getCount()
        for (i in 0 until count) {
            if (position == i) {
                if (reset) {
                    remove(position, transaction)
                    add(position, transaction)
                } else {
                    show(i, transaction)
                }
            } else {
                hide(i, transaction)
            }
        }
        if (allowingStateLoss) {
            transaction.commitAllowingStateLoss()
        } else {
            transaction.commit()
        }
    }

    /**
     * reset all the fragments and show current fragment
     *
     * @see .resetFragments
     */
    fun resetFragments() {
        resetFragments(mCurrentPosition)
    }

    /**
     * @see .resetFragments
     */
    fun resetFragments(position: Int) {
        resetFragments(position, false)
    }

    /**
     * reset all the fragment and show given position fragment
     *
     * @param position          fragment position
     * @param allowingStateLoss true if allowing state loss otherwise false
     */
    fun resetFragments(position: Int, allowingStateLoss: Boolean) {
        mCurrentPosition = position
        val transaction = mFragmentManager!!.beginTransaction()
        removeAll(transaction)
        add(position, transaction)
        if (allowingStateLoss) {
            transaction.commitAllowingStateLoss()
        } else {
            transaction.commit()
        }
    }

    /**
     * @see .removeAllFragment
     */
    fun removeAllFragment() {
        removeAllFragment(false)
    }

    /**
     * remove all fragment in the [FragmentManager]
     *
     * @param allowingStateLoss true if allowing state loss otherwise false
     */
    fun removeAllFragment(allowingStateLoss: Boolean) {
        val transaction = mFragmentManager!!.beginTransaction()
        removeAll(transaction)
        if (allowingStateLoss) {
            transaction.commitAllowingStateLoss()
        } else {
            transaction.commit()
        }
    }

    /**
     * @return current showing fragment's position
     */
    fun getCurrentPosition(): Int {
        return mCurrentPosition
    }

    /**
     * Also @see #getFragment(int)
     *
     * @return current position fragment
     */
    fun getCurrentFragment(): Fragment? {
        return getFragment(mCurrentPosition)
    }

    /**
     * Get the fragment has been added in the given position. Return null if the fragment
     * hasn't been added in [FragmentManager] or has been removed already.
     *
     * @param position position of fragment in [FragmentNavigatorAdapterKt.onCreateFragment]}
     * and [FragmentNavigatorAdapterKt.getTag]
     * @return The fragment if found or null otherwise.
     */
    fun getFragment(position: Int): Fragment? {
        val tag = mAdapter!!.getTag(position)
        return mFragmentManager!!.findFragmentByTag(tag)
    }

    private fun show(position: Int, transaction: FragmentTransaction) {
        val tag = mAdapter!!.getTag(position)
        val fragment = mFragmentManager!!.findFragmentByTag(tag)
        if (fragment == null) {
            add(position, transaction)
        } else {
            transaction.show(fragment)
        }
    }

    private fun hide(position: Int, transaction: FragmentTransaction) {
        val tag = mAdapter!!.getTag(position)
        val fragment = mFragmentManager!!.findFragmentByTag(tag)
        if (fragment != null) {
            transaction.hide(fragment)
        }
    }

    private fun add(position: Int, transaction: FragmentTransaction) {
        val fragment = mAdapter!!.onCreateFragment(position)
        val tag = mAdapter!!.getTag(position)
        transaction.add(mContainerViewId, fragment, tag)
    }

    private fun removeAll(transaction: FragmentTransaction) {
        val count = mAdapter!!.getCount()
        for (i in 0 until count) {
            remove(i, transaction)
        }
    }

    private fun remove(position: Int, transaction: FragmentTransaction) {
        val tag = mAdapter!!.getTag(position)
        val fragment = mFragmentManager!!.findFragmentByTag(tag)
        if (fragment != null) {
            transaction.remove(fragment)
        }
    }

    fun setDefaultPosition(defaultPosition: Int) {
        mDefaultPosition = defaultPosition
        if (mCurrentPosition == -1) {
            mCurrentPosition = defaultPosition
        }
    }
}