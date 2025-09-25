package de.taz.app.android.coachMarks

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter


class CoachMarkPagerAdapter(
    fragment: CoachMarkDialog,
) : FragmentStateAdapter(fragment) {
    private val coachMarks = fragment.coachMarks

    override fun createFragment(position: Int): Fragment {
        val coachMarkFragment = coachMarks[position]
        return coachMarkFragment
    }

    override fun getItemCount(): Int {
        return coachMarks.size
    }
}