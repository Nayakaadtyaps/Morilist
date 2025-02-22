package com.moriboot.morilist.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.moriboot.morilist.R

class ProductivityFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate layout fragment_account.xml
        val view = inflater.inflate(R.layout.fragment_productivity, container, false)
        return view
    }
}
