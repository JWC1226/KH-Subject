// 게임 카드 호버 시 우측 프리뷰 영역에 배경 이미지 표시
document.addEventListener('DOMContentLoaded', function () {
    var previewArea = document.getElementById('gamePreviewArea');
    if (!previewArea) return;

    var cards = document.querySelectorAll('.game-card[data-image]');
    cards.forEach(function (card) {
        card.addEventListener('mouseenter', function () {
            previewArea.style.backgroundImage = "url('" + card.dataset.image + "')";
            previewArea.style.backgroundSize = 'cover';
            previewArea.style.backgroundPosition = 'center';
        });
        card.addEventListener('mouseleave', function () {
            previewArea.style.backgroundImage = '';
        });
    });
});

// 메인 배너 자동 슬라이드 (게임 목록 이미지들을 순환, 양끝에 복제 슬라이드를 둬서 끊김 없이 무한 순환)
document.addEventListener('DOMContentLoaded', function () {
    var track = document.getElementById('sliderTrack');
    if (!track) return;

    var realSlides = track.querySelectorAll('.slide');
    var realCount = realSlides.length;
    if (realCount <= 1) return;

    var firstClone = realSlides[0].cloneNode(true);
    var lastClone = realSlides[realCount - 1].cloneNode(true);
    track.appendChild(firstClone);
    track.insertBefore(lastClone, realSlides[0]);

    var totalCount = realCount + 2;
    var currentIndex = 1; // 실제 첫 슬라이드(복제된 마지막 슬라이드 다음) 위치에서 시작
    var intervalId = null;
    var AUTO_INTERVAL_MS = 5000;

    function setPosition(index, withTransition) {
        if (!withTransition) {
            track.style.transition = 'none';
        }
        track.style.transform = 'translateX(-' + (index * 100) + '%)';
        if (!withTransition) {
            track.offsetHeight; // 강제 리플로우로 transition 없이 즉시 이동시킴
            track.style.transition = 'transform 0.6s ease-in-out';
        }
    }

    setPosition(currentIndex, false);

    function goToSlide(index) {
        currentIndex = index;
        setPosition(currentIndex, true);
    }

    function nextSlide() { goToSlide(currentIndex + 1); }
    function prevSlide() { goToSlide(currentIndex - 1); }

    // 복제 슬라이드에 도착한 뒤 애니메이션이 끝나면, 보이는 그림은 그대로인 채 실제 슬라이드 위치로 순간 이동
    track.addEventListener('transitionend', function () {
        if (currentIndex === totalCount - 1) {
            currentIndex = 1;
            setPosition(currentIndex, false);
        } else if (currentIndex === 0) {
            currentIndex = realCount;
            setPosition(currentIndex, false);
        }
    });

    function startAutoSlide() {
        intervalId = setInterval(nextSlide, AUTO_INTERVAL_MS);
    }
    function resetAutoSlide() {
        if (intervalId) clearInterval(intervalId);
        startAutoSlide();
    }

    var prevBtn = document.getElementById('hidari');
    var nextBtn = document.getElementById('migi');

    if (prevBtn) {
        prevBtn.addEventListener('click', function () {
            prevSlide();
            resetAutoSlide();
        });
    }
    if (nextBtn) {
        nextBtn.addEventListener('click', function () {
            nextSlide();
            resetAutoSlide();
        });
    }

    startAutoSlide();
});

// 마이페이지: 회원 탈퇴 버튼 클릭 시 확인창 표시
document.addEventListener('DOMContentLoaded', function () {
    var withdrawForm = document.getElementById('withdrawForm');
    if (!withdrawForm) return;

    withdrawForm.addEventListener('submit', function (e) {
        if (!confirm('진짜 정말로 탈퇴하실겁니까? 후회하지 않으실건가요?')) {
            e.preventDefault();
        }
    });
});
