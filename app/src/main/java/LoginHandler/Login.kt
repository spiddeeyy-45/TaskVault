package LoginHandler

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.example.taskvault.MainActivity
import com.example.taskvault.R
import com.example.taskvault.databinding.LoginBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging

class Login : AppCompatActivity() {
    lateinit var  Binding : LoginBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null) {
            startActivity(Intent(this,MainActivity::class.java))
            finish()
        }
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        Binding = DataBindingUtil.setContentView(this,R.layout.login)
        alreadyAccountHandler()
        loginButton()
        forgotPasswordHandler()
    }

    fun forgotPasswordHandler() {

        Binding.forgotPassword.setOnClickListener {

            val userEmail = Binding.emailInput.editText?.text.toString().trim()

            if (userEmail.isEmpty()) {
                Binding.emailInput.error = "Enter your registered email first"
                Binding.emailInput.requestFocus()
                return@setOnClickListener
            }

            FirebaseAuth.getInstance()
                .sendPasswordResetEmail(userEmail)
                .addOnCompleteListener { task ->

                    if (task.isSuccessful) {
                        Toast.makeText(
                            this,
                            "Password reset link sent to your email",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(
                            this,
                            task.exception?.localizedMessage ?: "Failed to send reset email",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
        }
    }

    fun loginButton(){
        Binding.loginBtn.setOnClickListener {
            loginuser(it)
        }
    }
    fun alreadyAccountHandler(){
        Binding.signupBtn.setOnClickListener(){
            val intent = Intent(this, SignUp::class.java)
            startActivity(intent)
        }
    }
    fun validateUserEmail(): Boolean {
        val userEmail =Binding.emailInput.editText?.text.toString()

        return when {
            userEmail.isEmpty() -> {
                Binding.emailInput.error = "Field cannot be empty"
                false
            }
            else -> {
                Binding.emailInput.error = null
                Binding.emailInput.isErrorEnabled = false
                true
            }
        }
    }
    fun validatePass(): Boolean {
        val password = Binding.passwordInput.editText?.text.toString()

        return when {
            password.isEmpty() -> {
                Binding.passwordInput.error = "Field cannot be empty"
                false
            }
            else -> {
                Binding.passwordInput.error = null
                Binding.passwordInput.isErrorEnabled = false
                true
            }
        }
    }
    fun loginuser(view: View) {
        if (!validateUserEmail() || !validatePass()) {
            return

        } else {
            isUserValid()
        }
    }
    fun saveFcmToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@addOnSuccessListener

            FirebaseFirestore.getInstance()
                .collection("User")
                .document(uid)
                .update("fcmToken", token)
        }
    }

    fun isUserValid() {
        val userEmail = Binding.emailInput.editText?.text.toString().trim()
        val userPassword = Binding.passwordInput.editText?.text.toString().trim()

        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(userEmail, userPassword)
            .addOnCompleteListener { task ->

                if (task.isSuccessful) {
                    saveFcmToken()
                    Binding.emailInput.error = null
                    Binding.emailInput.isErrorEnabled = false
                    Binding.passwordInput.error = null
                    Binding.passwordInput.isErrorEnabled = false

                    val userUid = FirebaseAuth.getInstance().currentUser?.uid
                    val database = FirebaseFirestore.getInstance()

                    userUid?.let {
                        database.collection("User")
                            .document(it)
                            .get()
                            .addOnSuccessListener { userSnapshot ->
                                if (userSnapshot.exists()) {
                                    val intent = Intent(this, MainActivity::class.java)
                                    intent.putExtra("fullName", userSnapshot.getString("fullName"))
                                    intent.putExtra("email", userSnapshot.getString("email"))
                                    intent.putExtra("phoneNumber", userSnapshot.getString("phoneNumber"))
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                }
                            }
                    }

                } else {
                    when (task.exception) {
                        is com.google.firebase.auth.FirebaseAuthInvalidUserException -> {
                            Binding.emailInput.error = "Email not registered"
                            Binding.emailInput.requestFocus()
                        }

                        is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> {
                            Binding.passwordInput.error = "Incorrect password"
                            Binding.passwordInput.requestFocus()
                        }
                        else -> {
                            Toast.makeText(
                                this,
                                task.exception?.localizedMessage ?: "Login failed",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
    }

}