package com.moriboot.morilist.fragments

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.moriboot.morilist.LoginActivity
import com.moriboot.morilist.R

class AccountFragment : Fragment() {

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate layout fragment_account.xml
        val view = inflater.inflate(R.layout.fragment_account, container, false)

        // Referensi tombol logout
        val logoutButton: Button = view.findViewById(R.id.btnlogout)

        // Aksi ketika tombol logout ditekan
        logoutButton.setOnClickListener {
            logoutUser()
        }

        return view
    }

    private fun logoutUser() {
        AlertDialog.Builder(requireContext())
            .setTitle("Konfirmasi Logout")
            .setMessage("Yakin ingin logout? yang datang dan pergi..")
            .setPositiveButton("Logout") { _, _ ->
                val auth = FirebaseAuth.getInstance()

                // Logout dari Firebase
                auth.signOut()

                // Hapus sesi pengguna dari SharedPreferences
                val sharedPref = requireActivity().getSharedPreferences("AppPrefs", android.content.Context.MODE_PRIVATE)
                with(sharedPref.edit()) {
                    remove("USER_ID")
                    apply()
                }

                // Tutup semua aktivitas dan arahkan ke login
                val intent = Intent(requireContext(), LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

}
