package com.moriboot.morilist

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.moriboot.morilist.databinding.ActivityNavbarbuttomBinding
import com.moriboot.morilist.fragments.AccountFragment
import com.moriboot.morilist.fragments.ProductivityFragment
import com.moriboot.morilist.fragments.TodoFragment


class Navbarbuttom : AppCompatActivity() {

    private lateinit var binding: ActivityNavbarbuttomBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavbarbuttomBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Replace fragment dengan fragment Todo secara default
        replaceFragment(TodoFragment())

        // Set listener untuk BottomNavigationView
        binding.bottomNavigationView.setOnItemSelectedListener {

            when(it.itemId) {
                R.id.nav_todo -> replaceFragment(TodoFragment())
                R.id.nav_productivity -> replaceFragment(ProductivityFragment())
                R.id.nav_account -> replaceFragment(AccountFragment())
                else -> {
                    // Jangan lupa tambahkan handling lainnya jika diperlukan
                }
            }
            true
        }

        // Contoh untuk menunjukkan AlertDialog jika diperlukan
        showAlertDialog()
    }

    private fun replaceFragment(fragment: Fragment) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.frame_layout, fragment)
        fragmentTransaction.commit()
    }

    // Fungsi untuk menampilkan AlertDialog
    private fun showAlertDialog() {
        val alertDialogBuilder = AlertDialog.Builder(this)
            .setTitle("Title")
            .setMessage("This is an alert message")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .create()

        alertDialogBuilder.show()
    }
}
