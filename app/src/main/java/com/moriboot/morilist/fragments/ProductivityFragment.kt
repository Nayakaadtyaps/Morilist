package com.moriboot.morilist.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moriboot.morilist.R
import com.moriboot.morilist.ShopActivity

class ProductivityFragment : Fragment() {

    private lateinit var pointsTextView: TextView
    private lateinit var pointsIconImageView: ImageView
    private lateinit var horseContainer: LinearLayout
    private lateinit var horseNameTextView: TextView
    private lateinit var horseLevelProgressBar: ProgressBar
    private lateinit var horseHungerProgressBar: ProgressBar
    private lateinit var horseThirstProgressBar: ProgressBar
    private lateinit var horseFitnessProgressBar: ProgressBar
    private lateinit var horseImageView: ImageView
    private lateinit var feedButton: Button // Tombol untuk memberi makan
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 300_000L // 5 menit dalam milidetik

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_productivity, container, false)
        pointsTextView = view.findViewById(R.id.tv_points)
        pointsIconImageView = view.findViewById(R.id.iv_points_icon)
        horseContainer = view.findViewById(R.id.horse_container)
        horseNameTextView = view.findViewById(R.id.tv_horse_name)
        horseLevelProgressBar = view.findViewById(R.id.pb_horse_level)
        horseHungerProgressBar = view.findViewById(R.id.pb_horse_hunger)
        horseThirstProgressBar = view.findViewById(R.id.pb_horse_thirst)
        horseFitnessProgressBar = view.findViewById(R.id.pb_horse_fitness)
        horseImageView = view.findViewById(R.id.iv_horse)
        feedButton = view.findViewById(R.id.btn_feed_horse) // Tambahkan tombol di layout

        val toolbar = view.findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        (requireActivity() as AppCompatActivity).setSupportActionBar(toolbar)
        setHasOptionsMenu(true)

        val userId = auth.currentUser?.uid ?: getSavedUserId()
        if (userId == null) {
            Log.e("Productivity", "User ID tidak ditemukan! Pengguna harus login.")
            pointsTextView.text = "Error (Silakan login)"
            return view
        }

        Log.d("Productivity", "Menggunakan User ID: $userId")
        listenToPoints(userId)
        listenToHorse(userId)
        startStatusDecay(userId) // Mulai pengurangan status

        // Event listener untuk tombol memberi makan
        feedButton.setOnClickListener {
            feedHorse(userId)
        }

        return view
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.productivity_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_cart -> {
                Log.d("Productivity", "Ikon keranjang diklik")
                val intent = Intent(requireContext(), ShopActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun listenToPoints(userId: String) {
        db.collection("users").document(userId)
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    pointsTextView.text = "Error (${e.message})"
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val points = snapshot.getLong("points") ?: 0L
                    pointsTextView.text = points.toString()
                    updateHorseLevel(userId, points) // Cek dan update level
                } else {
                    initializeUserPoints(userId)
                }
            }
    }

    private fun listenToHorse(userId: String) {
        db.collection("users").document(userId).collection("horse").document("data")
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.e("Productivity", "Horse listener gagal: ${e.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    horseContainer.visibility = View.VISIBLE
                    val name = snapshot.getString("name") ?: "Kuda Anda"
                    val level = snapshot.getLong("level")?.toInt() ?: 1
                    val hunger = snapshot.getLong("hunger")?.toInt() ?: 100
                    val thirst = snapshot.getLong("thirst")?.toInt() ?: 100
                    val fitness = snapshot.getLong("fitness")?.toInt() ?: 100

                    horseNameTextView.text = name
                    horseLevelProgressBar.progress = level
                    horseHungerProgressBar.progress = hunger
                    horseThirstProgressBar.progress = thirst
                    horseFitnessProgressBar.progress = fitness

                    when (name) {
                        "Normal Horse" -> horseImageView.setImageResource(R.drawable.kuda)
                        "Twilight Horse" -> horseImageView.setImageResource(R.drawable.twaylaig)
                        else -> horseImageView.setImageResource(R.drawable.kuda)
                    }
                } else {
                    horseContainer.visibility = View.GONE
                }
            }
    }

    private fun initializeUserPoints(userId: String) {
        if (auth.currentUser == null) {
            pointsTextView.text = "Error (Silakan login)"
            return
        }
        db.collection("users").document(userId)
            .set(mapOf("points" to 0L))
            .addOnSuccessListener {
                pointsTextView.text = "0"
            }
            .addOnFailureListener { ex ->
                pointsTextView.text = "Error (${ex.message})"
            }
    }

    private fun getSavedUserId(): String? {
        val sharedPref = requireActivity().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        return sharedPref.getString("USER_ID", null)
    }

    private fun startStatusDecay(userId: String) {
        val horseDocRef = db.collection("users").document(userId).collection("horse").document("data")

        handler.postDelayed(object : Runnable {
            override fun run() {
                horseDocRef.get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val hunger = snapshot.getLong("hunger")?.toInt() ?: 100
                        val thirst = snapshot.getLong("thirst")?.toInt() ?: 100
                        val fitness = snapshot.getLong("fitness")?.toInt() ?: 100

                        // Kurangi status (misalnya -10 per 5 menit)
                        val newHunger = (hunger - 10).coerceAtLeast(0)
                        val newThirst = (thirst - 10).coerceAtLeast(0)
                        val newFitness = (fitness - 5).coerceAtLeast(0)

                        // Update ke Firestore
                        horseDocRef.update(
                            mapOf(
                                "hunger" to newHunger,
                                "thirst" to newThirst,
                                "fitness" to newFitness
                            )
                        ).addOnFailureListener { e ->
                            Log.e("Productivity", "Gagal update status: ${e.message}")
                        }
                    }
                }
                // Jadwalkan ulang setiap 5 menit
                handler.postDelayed(this, updateInterval)
            }
        }, updateInterval)
    }

    private fun updateHorseLevel(userId: String, currentPoints: Long) {
        val horseDocRef = db.collection("users").document(userId).collection("horse").document("data")
        horseDocRef.get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val currentLevel = snapshot.getLong("level")?.toInt() ?: 1
                val pointsPerLevel = 100 // 100 poin = 1 level

                val newLevel = (currentPoints / pointsPerLevel).toInt() + 1
                if (newLevel > currentLevel) {
                    horseDocRef.update("level", newLevel)
                        .addOnSuccessListener {
                            Log.d("Productivity", "Level naik menjadi $newLevel")
                        }
                }
            }
        }
    }

    private fun feedHorse(userId: String) {
        val horseDocRef = db.collection("users").document(userId).collection("horse").document("data")
        val userDocRef = db.collection("users").document(userId)

        userDocRef.get().addOnSuccessListener { userSnapshot ->
            val points = userSnapshot.getLong("points") ?: 0L
            if (points >= 10) { // Butuh 10 poin untuk memberi makan
                horseDocRef.update("hunger", 100)
                userDocRef.update("points", points - 10)
                    .addOnSuccessListener {
                        Log.d("Productivity", "Kuda diberi makan, poin berkurang 10")
                    }
            } else {
                pointsTextView.text = "Poin tidak cukup!"
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null) // Hentikan timer saat fragment dihancurkan
    }
}