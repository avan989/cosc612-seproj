package com.example.t2cc;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegistrationActivity extends AppCompatActivity implements
    View.OnClickListener {

  private final static String TAG = "T2CC:Registration";
  String mStudentsCollection = "students";
  // Firebase Auth
  private FirebaseAuth mAuth;
  private FirebaseFirestore mFBDB;
  private FirebaseAuth.AuthStateListener mAuthListener;

  // Form Details
  private EditText mFirstNameField;
  private EditText mLastNameField;
  private EditText mEmailField;
  private EditText mPasswordField;
  private EditText mPasswordConfirmationField;

  //Button
  private Button mRegisterButton;
  private AuthUI mAuthUI;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_registration);

    // registration info
    mFirstNameField = findViewById(R.id.firstNameRegField);
    mLastNameField = findViewById(R.id.lastNameRegField);
    mEmailField = findViewById(R.id.emailRegField);
    mPasswordField = findViewById(R.id.passwordRegField);
    mPasswordConfirmationField = findViewById(R.id.confirmPasswordRegField);

    // Buttons
    mRegisterButton = findViewById(R.id.registrationButton);
    mRegisterButton.setOnClickListener(this);

    //firebase Auth
    mAuth = FirebaseAuth.getInstance();
    mAuthUI = AuthUI.getInstance();
    mFBDB = FirebaseFirestore.getInstance();
    mAuthListener = new FirebaseAuth.AuthStateListener() {
      @Override
      public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
          if (user.isEmailVerified()) {
            Log.d(TAG, "emailVerified:success");
            addToStudentDatabase(user);
          }
        }
      }
    };
  }

  @Override
  public void onStart() {//check if user is signed in
    super.onStart();
  }

  @Override
  public void onClick(View v) {
    int i = v.getId();
    if (i == R.id.registrationButton) {
      registerUser(mFirstNameField.getText().toString(), mLastNameField.getText().toString(),
          mEmailField.getText().toString(), mPasswordField.getText().toString());
    }
  }

  private void changeToLoginActivity() {
    Intent intent = new Intent(RegistrationActivity.this, LoginActivity.class);
    startActivity(intent);
  }

  private void registerUser(String fName, String lName, String email, String password) {
    Log.d(TAG, "register:" + email);
    if (!validateRegistrationForm()) {
      return;
    }

    final String userFullName = String.format("%s %s", fName, lName);

    mAuth.createUserWithEmailAndPassword(email, password)
        .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
          @Override
          public void onComplete(@NonNull Task<AuthResult> task) {
            if (task.isSuccessful()) {
              // registration success, update UI with the signed-in user's information
              Log.d(TAG, "createUserWithEmail:success");
              FirebaseUser user = mAuth.getCurrentUser();
              mAuth.addAuthStateListener(mAuthListener);
              // populate profile with name info
              updateUserProfile(user, userFullName);
              // do email verification
              sendEmailVerification();
              mAuthUI.signOut(RegistrationActivity.this); //sign out automatically signed in user
              Toast.makeText(RegistrationActivity.this, "Creating Account",
                  Toast.LENGTH_LONG).show();
              changeToLoginActivity();
            } else {
              // registration fails, display a message to the user.
              Exception genException = task.getException();
              Log.w(TAG, "createUserWithEmail:failure", genException);
              try {
                throw genException;
              } catch (FirebaseAuthWeakPasswordException e) {
                mPasswordField.setError(genException.getMessage());
                mPasswordField.requestFocus();
              } catch (FirebaseAuthUserCollisionException | FirebaseAuthInvalidCredentialsException e) {
                String m = e.getMessage();
                if( m.contains("email"))  {
                  mEmailField.setError(e.getMessage());
                  mEmailField.requestFocus();
                } else {
                  mPasswordField.setError(e.getMessage());
                  mPasswordField.requestFocus();
                }
              } catch (Exception e) {
                Toast.makeText(RegistrationActivity.this, "Registration failed.\n" + genException.getMessage(),
                    Toast.LENGTH_LONG).show();
              }
            }
          }
        });
  }

  private void addToStudentDatabase(FirebaseUser user) {
    if (user != null) {
      String mEmail = user.getEmail();
      String[] mFullName = user.getDisplayName().split(" ");
      String mFirstName = mFullName[0];
      String mLastName = mFullName[1];

      Map<String, Object> student = new HashMap<>();
      student.put("fname", mFirstName);
      student.put("lname", mLastName);
      student.put("email", mEmail);

      mFBDB.collection(mStudentsCollection)
          .add(student)
          .addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
            @Override
            public void onSuccess(DocumentReference documentReference) {
              Log.d(TAG, "studentAdded:" + documentReference.getId());
              mAuth.removeAuthStateListener(mAuthListener);
            }
          })
          .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
              Log.w(TAG, "studentAdded:failure", e);
            }
          });
    }
  }

  private boolean validateRegistrationForm() {
    boolean valid = true;

    String firstName = mFirstNameField.getText().toString();
    if (TextUtils.isEmpty(firstName)) {
      mFirstNameField.setError("Required.");
      mFirstNameField.requestFocus();
      valid = false;
    } else {
      mFirstNameField.setError(null);
    }

    String lastName = mLastNameField.getText().toString();
    if (TextUtils.isEmpty(lastName)) {
      mLastNameField.setError("Required.");
      mLastNameField.requestFocus();
      valid = false;
    } else {
      mLastNameField.setError(null);
    }

    String email = mEmailField.getText().toString();
    if (TextUtils.isEmpty(email)) {
      mEmailField.setError("Required.");
      mEmailField.requestFocus();
      valid = false;
    } else {
      mEmailField.setError(null);
    }

    String password = mPasswordField.getText().toString();
    if (TextUtils.isEmpty(password)) {
      mPasswordField.setError("Required.");
      mPasswordField.requestFocus();
      valid = false;
    } else {
      mPasswordField.setError(null);
    }

    String passwordConfirm = mPasswordConfirmationField.getText().toString();
    if (TextUtils.isEmpty(passwordConfirm)) {
      mPasswordConfirmationField.setError("Required.");
      mPasswordConfirmationField.requestFocus();
      valid = false;
    } else if (!password.equals(passwordConfirm)) {
      mPasswordConfirmationField.setError("Passwords don't match.");
      mPasswordConfirmationField.requestFocus();
      valid = false;
    } else {
      mPasswordConfirmationField.setError(null);
    }

    return valid;
  }

  private void updateUserProfile(FirebaseUser user, String userName) {
    if (user != null) {
      UserProfileChangeRequest profileUpdates = new UserProfileChangeRequest.Builder()
          .setDisplayName(userName)
          .build();

      user.updateProfile(profileUpdates)
          .addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
              if (task.isSuccessful()) {
                Log.d(TAG, "updateProfile:success");
              } else {
                Log.e(TAG, "updateProfile:failure", task.getException());
              }
            }
          });
    }
  }

  private void sendEmailVerification() {
    final FirebaseUser user = mAuth.getCurrentUser();
    user.sendEmailVerification()
        .addOnCompleteListener(new OnCompleteListener<Void>() {
          @Override
          public void onComplete(@NonNull Task<Void> task) {
            if (task.isSuccessful()) {
              Log.d(TAG, "sendEmailVerification:success");
            } else {
              Log.e(TAG, "sendEmailVerification:failure", task.getException());
            }
          }
        });
  }
}
