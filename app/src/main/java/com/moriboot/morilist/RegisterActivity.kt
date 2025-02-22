package com.moriboot.morilist


import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore


class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private var isPasswordVisible: Boolean = false
    private var isConfirmPasswordVisible: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Initialize FirebaseAuth and Firestore
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Get references to UI elements
        val etFullName: EditText = findViewById(R.id.etFullName)
        val etEmail: EditText = findViewById(R.id.etEmail)
        val etPasswordSignUp: EditText = findViewById(R.id.etPasswordSignUp)
        val etConfirmPassword: EditText = findViewById(R.id.etConfirmPassword)
        val btnSignUp: Button = findViewById(R.id.btnSignUp)
        val tvRedirectToSignIn: TextView = findViewById(R.id.tvRedirectToSignIn)
        val ivTogglePassword: ImageView = findViewById(R.id.ivTogglePassword)
        val ivToggleConfirmPassword: ImageView = findViewById(R.id.ivToggleConfirmPassword)

        // Toggle password visibility
        ivTogglePassword.setOnClickListener {
            isPasswordVisible = !isPasswordVisible
            if (isPasswordVisible) {
                etPasswordSignUp.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                ivTogglePassword.setImageResource(R.drawable.baseline_vpn_key_24)
            } else {
                etPasswordSignUp.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                ivTogglePassword.setImageResource(R.drawable.baseline_vpn_key_off_24)
            }
            etPasswordSignUp.setSelection(etPasswordSignUp.text.length) // Set cursor position
        }

        // Toggle confirm password visibility
        ivToggleConfirmPassword.setOnClickListener {
            isConfirmPasswordVisible = !isConfirmPasswordVisible
            if (isConfirmPasswordVisible) {
                etConfirmPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                ivToggleConfirmPassword.setImageResource(R.drawable.baseline_vpn_key_off_24)
            } else {
                etConfirmPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                ivToggleConfirmPassword.setImageResource(R.drawable.baseline_vpn_key_24)
            }
            etConfirmPassword.setSelection(etConfirmPassword.text.length) // Set cursor position
        }

        // Handle "Sign Up" button click
        btnSignUp.setOnClickListener {
            val fullName = etFullName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPasswordSignUp.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (fullName.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Create user with Firebase Authentication
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        // Sign up success
                        val userId = auth.currentUser?.uid

                        // Create user data with level default
                        val userData = hashMapOf(
                            "fullName" to fullName,
                            "email" to email,
                            "level" to "Beginner" // Default level
                        )

                        if (userId != null) {
                            db.collection("users").document(userId)
                                .set(userData)
                                .addOnSuccessListener {
                                    Toast.makeText(this, "Sign Up Successful!", Toast.LENGTH_SHORT).show()
                                    // Redirect to HomeActivity
                                    val intent = Intent(this, LoginActivity::class.java)
                                    startActivity(intent)
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Failed to save user data: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        // If sign up fails, display a message to the user
                        val errorMessage = task.exception?.message ?: "Sign Up Failed"
                        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
        }

        // Create a SpannableString for "Already Have An Account? Sign In"
        val text = "Already Have An Account? Sign In"
        val spannableString = SpannableString(text)

        // Define the clickable action for "Sign In"
        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                // Redirect to LoginActivity
                val intent = Intent(this@RegisterActivity, LoginActivity::class.java)
                startActivity(intent)
            }
        }

        // Apply styles to "Sign In"
        val startIndex = text.indexOf("Sign In")
        val endIndex = startIndex + "Sign In".length
        spannableString.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spannableString.setSpan(ForegroundColorSpan(Color.BLUE), startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Set the SpannableString to the TextView
        tvRedirectToSignIn.text = spannableString
        tvRedirectToSignIn.movementMethod = LinkMovementMethod.getInstance()
    }
}
