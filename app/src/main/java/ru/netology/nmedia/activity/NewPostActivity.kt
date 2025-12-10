package ru.netology.nmedia.activity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import ru.netology.nmedia.databinding.ActivityNewPostBinding
import ru.netology.nmedia.util.AndroidUtils

class NewPostActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_POST_CONTENT = "EXTRA_POST_CONTENT"
        const val RESULT_EDITED_POST = "RESULT_EDITED_POST"
        const val EXTRA_NEW_POST_CONTENT = "EXTRA_NEW_POST_CONTENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val binding = ActivityNewPostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Определяем режим работы: редактирование или создание нового поста
        val isEditMode = intent.extras?.containsKey(EXTRA_POST_CONTENT) == true
        val initialContent = intent.getStringExtra(EXTRA_POST_CONTENT) ?: ""

        // Заполняем текст редактором в зависимости от режима
        binding.edit.setText(initialContent)

        // Показываем клавиатуру
        AndroidUtils.showKeyboard(binding.edit)

        // Добавляем наблюдатель за изменением текста
        binding.edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Переключаем видимость крестика в зависимости от наличия текста
                binding.cancelEdit.visibility =
                    if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Обработчик клика по крестику: возврат на MainActivity
        binding.cancelEdit.setOnClickListener {
            finish() // Завершаем текущую активность (переходим на предыдущую)
        }

        // Обработчик OK-кнопки
        binding.ok.setOnClickListener {
            if (binding.edit.text.isNullOrBlank()) {
                setResult(Activity.RESULT_CANCELED)
            } else {
                val intent = Intent()
                val content = binding.edit.text.toString()
                intent.putExtra(if (isEditMode) RESULT_EDITED_POST else EXTRA_POST_CONTENT, content)
                setResult(Activity.RESULT_OK, intent)
            }
            finish()
        }
    }
}