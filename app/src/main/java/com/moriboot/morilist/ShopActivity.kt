package com.moriboot.morilist

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ShopActivity : AppCompatActivity() {

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var selectedHorse: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.shop)

        val ivHorseNormal = findViewById<ImageView>(R.id.iv_horse_normal)
        val ivHorseTwilight = findViewById<ImageView>(R.id.iv_horse_twilight)
        val ivFoodOats = findViewById<ImageView>(R.id.iv_food_oats)
        val ivFoodLucerne = findViewById<ImageView>(R.id.iv_food_lucerne)
        val ivDrinkJuice = findViewById<ImageView>(R.id.iv_drink_juice)
        val ivDrinkWater = findViewById<ImageView>(R.id.iv_drink_water)

        ivHorseNormal.setOnClickListener { buyItem("horse_normal", 100) }
        ivHorseTwilight.setOnClickListener { buyItem("horse_twilight", 50000) }
        ivFoodOats.setOnClickListener { buyItem("food_oats", 15) }
        ivFoodLucerne.setOnClickListener { buyItem("food_lucerne", 25) }
        ivDrinkJuice.setOnClickListener { buyItem("drink_juice", 50) }
        ivDrinkWater.setOnClickListener { buyItem("drink_water", 5) }
    }

    private fun buyItem(itemType: String, cost: Long) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "Silakan login terlebih dahulu", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                val points = document.getLong("points") ?: 0L
                if (points >= cost) {
                    when (itemType) {
                        "horse_normal", "horse_twilight" -> {
                            selectedHorse = if (itemType == "horse_normal") "Normal Horse" else "Twilight Horse"
                            val horseData = mapOf(
                                "name" to selectedHorse,
                                "level" to 1L,
                                "hunger" to 100L,
                                "thirst" to 100L,
                                "fitness" to 100L
                            )
                            db.collection("users").document(userId).collection("horse").document("data")
                                .set(horseData)
                                .addOnSuccessListener {
                                    db.collection("users").document(userId)
                                        .update("points", points - cost)
                                    Toast.makeText(this, "Kuda $selectedHorse berhasil dibeli!", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        "food_oats", "food_lucerne" -> {
                            db.collection("users").document(userId).collection("horse").document("data")
                                .get()
                                .addOnSuccessListener { horseDoc ->
                                    if (horseDoc.exists()) {
                                        val hunger = horseDoc.getLong("hunger") ?: 0L
                                        val newHunger = (hunger + if (itemType == "food_oats") 20L else 30L).coerceAtMost(100L)
                                        db.collection("users").document(userId).collection("horse").document("data")
                                            .update("hunger", newHunger)
                                        db.collection("users").document(userId)
                                            .update("points", points - cost)
                                        Toast.makeText(this, "Pakan ${if (itemType == "food_oats") "Oats" else "Lucerne"} berhasil dibeli, lapar +${if (itemType == "food_oats") 20 else 30}%!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this, "Belum punya kuda!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                        "drink_juice", "drink_water" -> {
                            db.collection("users").document(userId).collection("horse").document("data")
                                .get()
                                .addOnSuccessListener { horseDoc ->
                                    if (horseDoc.exists()) {
                                        val thirst = horseDoc.getLong("thirst") ?: 0L
                                        val newThirst = (thirst + if (itemType == "drink_juice") 40L else 20L).coerceAtMost(100L)
                                        db.collection("users").document(userId).collection("horse").document("data")
                                            .update("thirst", newThirst)
                                        db.collection("users").document(userId)
                                            .update("points", points - cost)
                                        Toast.makeText(this, "Minuman ${if (itemType == "drink_juice") "Juice" else "Water"} berhasil dibeli, haus +${if (itemType == "drink_juice") 40 else 20}%!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(this, "Belum punya kuda!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                        }
                    }
                } else {
                    Toast.makeText(this, "Poin tidak cukup!", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Gagal memeriksa poin: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}