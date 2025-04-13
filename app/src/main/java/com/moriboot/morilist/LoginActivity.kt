package com.moriboot.morilist

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
//    private lateinit var googleSignInClient: GoogleSignInClient
//    private val RC_SIGN_IN = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)


        auth = FirebaseAuth.getInstance()


//        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
//            .requestIdToken(getString(R.string.gcm_defaultSenderId))
//            .requestEmail()
//            .build()
//        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val etUsername: EditText = findViewById(R.id.etUsername)
        val etPassword: EditText = findViewById(R.id.etPassword)
        val btnSignIn: Button = findViewById(R.id.btnSignIn)
        val tvSignUp: TextView = findViewById(R.id.tvSignUp)

        btnSignIn.setOnClickListener {
            val email = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            // Validasi input kosong
            if (email.isEmpty()) {
                Toast.makeText(this, "Isi email tuan muda", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                Toast.makeText(this, "Isi password tuan muda", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            // Login dengan Firebase
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null) {
                            // Simpan status login dan data user
                            saveLoginState(user.uid)
                            saveUserData(
                                user.displayName ?: "User",
                                user.email ?: "No Email",
                                "surfing"
                            )
                            Toast.makeText(this, "Asik bisaa", Toast.LENGTH_SHORT).show()

                            // Pindah ke MainActivity
                            val intent = Intent(this, Navbarbuttom::class.java)
                            startActivity(intent)
                            finish()
                        }
                    } else {
                        // Penanganan error lebih spesifik
                        val errorMessage = when (task.exception?.message) {
                            "The password is invalid or the user does not have a password." -> "Password salah tuan muda"
                            "There is no user record corresponding to this identifier. The user may have been deleted." -> "Email tidak ditemukan tuan muda"
                            "The email address is badly formatted." -> "Format email salah tuan muda"
                            else -> "Login gagal:  Ada yang salah bos"
                        }
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Buat SpannableString untuk teks hyperlink
        val spannableText = SpannableString("Don't Have An Account? Sign Up")
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
                startActivity(intent)
            }
        }
        spannableText.setSpan(clickableSpan, 23, spannableText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableText.setSpan(ForegroundColorSpan(Color.BLUE), 23, spannableText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        tvSignUp.text = spannableText
        tvSignUp.movementMethod = LinkMovementMethod.getInstance()
        tvSignUp.highlightColor = Color.TRANSPARENT
    }

    override fun onStart() {
        super.onStart()
        // Cek apakah pengguna sudah login
        if (isUserLoggedIn()) {
            val intent = Intent(this, Navbarbuttom::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun saveLoginState(userId: String) {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        with(sharedPref.edit()) {
            putString("USER_ID", userId)
            putBoolean("IS_LOGGED_IN", true)
            apply()
        }
    }

    private fun isUserLoggedIn(): Boolean {
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val isLoggedIn = sharedPref.getBoolean("IS_LOGGED_IN", false)
        val userId = sharedPref.getString("USER_ID", null)
        return isLoggedIn && userId != null && auth.currentUser != null
    }

    private fun saveUserData(username: String, email: String, level: String) {
        val db = FirebaseFirestore.getInstance()
        val userData = hashMapOf(
            "username" to username,
            "email" to email,
            "level" to level
        )

        db.collection("users").document(email)
            .set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Data saved successfully", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to save data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}