package LoginHandler

import DataClass.UserHelperClass
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.taskvault.databinding.SignUpBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging


class SignUp : AppCompatActivity() {
    private lateinit var Binding: SignUpBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        Binding = SignUpBinding.inflate(layoutInflater)
        setContentView(Binding.root)
        //signUpButton
        signUpButtonHandler()
        //alreadyAccountButton
        alreadyAccountHandler()

        }
    fun signUpButtonHandler() {
        Binding.signupBtn.setOnClickListener {
            val fullName = Binding.fullnameInput.editText?.text.toString()
            val phone = Binding.phoneInput.editText?.text.toString()
            val email = Binding.emailInput.editText?.text.toString()
            val password = Binding.passwordInput.editText?.text.toString()

            val isFullNameValid = validateFullname()
            val isEmailValid = validateEmail()
            val isPhoneNoValid = validatephoneno()
            val isPasswordValid = validatepass()

            if (!isFullNameValid || !isEmailValid || !isPhoneNoValid || !isPasswordValid) {
                Toast.makeText(this, "Please fix input errors", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (fullName.isEmpty()||phone.isEmpty()||email.isEmpty()||password.isEmpty()){
                Toast.makeText(this,"Fields Can Not Be Empty!",Toast.LENGTH_SHORT).show()
            }
            else{
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(email,password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            val user = UserHelperClass(fullName, email, phone, password)

                        if (uid != null) {
                            FirebaseFirestore.getInstance()
                                .collection("User")
                                .document(uid)
                                .set(user)
                                .addOnSuccessListener {
                                    saveFcmToken()
                                    val intent = Intent(this, Login::class.java)
                                    startActivity(intent)

                                    Toast.makeText(this, "You are Welcome to TaskVault", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, "Error saving user data: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                        else {
                            Toast.makeText(this, "Authentication failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                        }
                    }
            }
        }
    }
    fun alreadyAccountHandler(){
        Binding.alreadyLogin.setOnClickListener {
            val intent = Intent(this,Login::class.java)
            startActivity(intent)
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

    fun validatepass():Boolean{
        val password = Binding.passwordInput.editText?.text.toString()
        val passwordRegex = ("^" +
                "(?=.*[0-9])" +         // at least one digit
                "(?=.*[a-z])" +         // at least one lowercase letter
                "(?=.*[A-Z])" +         // at least one uppercase letter
                "(?=.*[@#\$%^&!*+=_-])" +   // at least one special character
                "(?=\\S+$)" +           // no white spaces
                ".{8,}" +               // at least 8 characters
                "$").toRegex()
        return when {
            password.isEmpty() -> {
                Binding.passwordInput.error = "Field cannot be empty"
                false
            }

            !password.matches(passwordRegex) -> {
                Binding.passwordInput.error = "Password must include uppercase, lowercase, digit, special char, and no spaces"
                false
            }

            else -> {
                Binding.passwordInput.error = null
                true
            }
        }

    }
    fun validatephoneno():Boolean{
        val phoneNumber = Binding.phoneInput.editText?.text.toString()
        val phoneNumberRegex = "^(\\+\\d{1,3})?[0-9]{10}$".toRegex()
        return when {
            phoneNumber.isEmpty() -> {
                Binding.phoneInput.error = "Field cannot be empty"
                false
            }

            !phoneNumber.matches(phoneNumberRegex) -> {
                    Binding.phoneInput.error = "Enter a valid 10-digit phone number"
                false
            }

            else -> {
                Binding.phoneInput.error = null
                true
            }
        }

    }
    fun validateEmail():Boolean{
        val mail = Binding.emailInput.editText?.text.toString()
        val emailRegex = "^[A-Za-z0-9](?:[A-Za-z0-9._%+-]{0,63})@[A-Za-z0-9.-]+\\.com$".toRegex()

        return when {
            mail.isEmpty() -> {
                Binding.emailInput.error = "Field cannot be empty"
                false
            }

            !mail.matches(emailRegex) -> {
                Binding.emailInput.error = "Enter a valid email address ending with .com and without special characters at the beginning"
                false
            }

            else -> {
                Binding.emailInput.error = null
                true
            }
        }

    }
    fun validateFullname():Boolean{
        val name = Binding.fullnameInput.editText?.text.toString()
        val nameRegex = "^[A-Za-z]{2,}(?: [A-Za-z]{2,}){0,2}$".toRegex()


        return when {
            name.isEmpty() -> {
                Binding.fullnameInput.error = "Field cannot be empty"
                false
            }
            !name.matches(nameRegex) || name.length < 4 || name.length > 20 -> {
                Binding.fullnameInput.error = "Enter 1–3 words, 4–20 characters, letters only"
                false
            }
            else -> {
                Binding.fullnameInput.error = null
                true
            }
        }

    }
    }


