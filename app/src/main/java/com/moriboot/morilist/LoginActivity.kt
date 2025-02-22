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
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Initialize FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // Configure Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.gcm_defaultSenderId))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // References to UI elements
        val etUsername: EditText = findViewById(R.id.etUsername)
        val etPassword: EditText = findViewById(R.id.etPassword)
        val btnSignIn: Button = findViewById(R.id.btnSignIn)
        val tvSignUp: TextView = findViewById(R.id.tvSignUp)
        val btnGoogleSignIn: ImageButton = findViewById(R.id.btnGoogleSignIn)

        // Handle sign-in button click
        btnSignIn.setOnClickListener {
            val email = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Isi semua tuan muda", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }


            // Login with Firebase
            auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser
                        if (user != null) {
                            // Simpan data user ke Firestore
                            saveUserData(user.displayName ?: "User", user.email ?: "No Email", "surfing")
                        }

                        Toast.makeText(this, "Asik bisaa", Toast.LENGTH_SHORT).show()

                        // Redirect to MainActivity
                        val intent = Intent(this, Navbarbuttom::class.java)
                        startActivity(intent)
                        finish()
                    } else {
                        val errorMessage = task.exception?.message ?: "Ada yang salah bos"
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Handle Google Sign-In button click
        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        // Buat SpannableString untuk teks hyperlink
        val spannableText = SpannableString("Don't Have An Account? Sign Up")

        // Tambahkan ClickableSpan pada teks "Sign Up"
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Navigasi ke SignUpActivity
                val intent = Intent(this@LoginActivity, RegisterActivity::class.java)
                startActivity(intent)
            }
        }
        spannableText.setSpan(clickableSpan, 23, spannableText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Tambahkan warna biru pada teks "Sign Up"
        val colorSpan = ForegroundColorSpan(Color.BLUE)
        spannableText.setSpan(colorSpan, 23, spannableText.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Pasang teks ke TextView dan buat teksnya bisa diklik
        tvSignUp.text = spannableText
        tvSignUp.movementMethod = LinkMovementMethod.getInstance()
        tvSignUp.highlightColor = Color.TRANSPARENT // Hilangkan efek highlight saat diklik
    }
    override fun onStart() {
        super.onStart()
        val sharedPref = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val savedUserId = sharedPref.getString("USER_ID", null)

        if (savedUserId != null && FirebaseAuth.getInstance().currentUser != null) {
            val intent = Intent(this, Navbarbuttom::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        this.startActivityForResult(signInIntent, RC_SIGN_IN)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful) {
                val account = task.result
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential)
                    .addOnCompleteListener { authTask ->
                        if (authTask.isSuccessful) {
                            val user = auth.currentUser
                            if (user != null) {
                                // Simpan data user ke Firestore
                                saveUserData(user.displayName ?: "Google User", user.email ?: "No Email", "free")
                            }

                            Toast.makeText(this, "Login Successful!", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, RegisterActivity ::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this, "Login Failed: ${authTask.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
            }

        }
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
