package dev.solotilt.personal;

import java.util.*;

/** Effect catalog. Catalog order is display order; ids are the stable keys in saved settings and the shader file names. */
final class Effects {
    static final int DEPTH=1,TOUCH=2,ANIMATED=4,ANIMATED_SWITCH=8,WIPE=16,LAYER=32;
    /** LAYER: works as the second layer, drawn over another effect's result (it adds weather, light or grain). */
    static final String[] CATEGORY_RU={"Оптика","Свет и блики","Вода и стекло","Объём","Атмосфера","Ретро"};
    static final String[] CATEGORY_EN={"Optics","Light & sheen","Water & glass","Depth","Atmosphere","Retro"};

    static final class Effect {
        final String id,ru,en,aboutRu,aboutEn,aRu,aEn,bRu,bEn,switchRu,switchEn;final int category,flags;final float a,b;
        Effect(String id,int category,int flags,String ru,String en,String aboutRu,String aboutEn,String aRu,String aEn,float a,String bRu,String bEn,float b,String switchRu,String switchEn){
            this.id=id;this.category=category;this.flags=flags;this.ru=ru;this.en=en;this.aboutRu=aboutRu;this.aboutEn=aboutEn;
            this.aRu=aRu;this.aEn=aEn;this.a=a;this.bRu=bRu;this.bEn=bEn;this.b=b;this.switchRu=switchRu;this.switchEn=switchEn;
        }
        /** CPU physics for this effect, created fresh each time it becomes the current effect; null for pure shaders. */
        java.util.function.Supplier<Simulation> simulation;
        Effect sim(java.util.function.Supplier<Simulation> factory){simulation=factory;return this;}
        boolean has(int flag){return (flags&flag)!=0;}
        boolean hasSwitch(){return switchRu!=null;}
        /** Needs a frame every vsync rather than only while tilting; ANIMATED_SWITCH animates only with its switch on. */
        boolean animated(boolean switchOn){return has(ANIMATED)||has(ANIMATED_SWITCH)&&switchOn;}
        /** Default [a, b, switch]. Every switch defaults to on. */
        float[] defaults(){return new float[]{a,b,1};}
        String name(boolean en){return en?this.en:ru;}
        String about(boolean en){return en?aboutEn:aboutRu;}
        String category(boolean en){return (en?CATEGORY_EN:CATEGORY_RU)[category];}
    }

    static final Effect[] ALL={
        new Effect("classic",0,0,"Классика","Classic","Фото за наклоняющимся стеклом: перспектива, мягкое размытие и тень.","Your photo behind tilting glass: perspective, soft blur and shade.","Сила наклона","Tilt strength",.8f,"Плавность","Smoothness",.45f,null,null),
        new Effect("focus",0,DEPTH,"Перевод фокуса","Focus Rack","Наклон переводит фокус между ближним и дальним планом.","Tilt shifts focus between near and far.","Размытие","Blur",.55f,"Ход фокуса","Focus travel",.55f,null,null),
        new Effect("awaken",0,0,"Пробуждение","Awakening","При разблокировке фото проявляется из мягкого светлого размытия.","On unlock your photo surfaces from a soft, bright blur.","Размытие","Blur",.6f,"Длительность","Duration",.6f,null,null),
        new Effect("light",1,LAYER,"Живой свет","Dynamic Light","Луч и блики на рельефе фото следуют за наклоном.","A beam and surface glints follow your tilt.","Яркость","Brightness",.4f,"Тепло ↔ холод","Warm ↔ cold",.6f,"Резкость","Sharpen"),
        new Effect("holo",1,ANIMATED_SWITCH,"Голограмма","Holographic Foil","Радужный отлив на светлых участках и контурах.","Rainbow diffraction on highlights and edges.","Сила","Strength",.7f,"Порог","Threshold",.5f,"Микрорельеф","Micro-ridges"),
        new Effect("prism",1,0,"Призма","Spectral Prism","Контуры расщепляются в спектр при наклоне.","Edges split into a spectrum as you tilt.","Дисперсия","Dispersion",.65f,"Порог краёв","Edge threshold",.5f,"Блики граней","Facet glints"),
        new Effect("bokeh",1,0,"Боке","Bokeh","Мягкий расфокус: огни расцветают кружками и плывут при наклоне.","Soft defocus: highlights bloom into discs that drift as you tilt.","Размытие","Defocus",.6f,"Огни","Lights",.55f,"Форма диафрагмы","Hexagon aperture"),
        new Effect("oil",1,ANIMATED,"Масляная плёнка","Oil Slick","Переливы тонкой плёнки, как бензин на воде.","Swirling thin-film iridescence.","Сила","Strength",.7f,"Блеск","Sheen",.5f,null,null),
        new Effect("charging",1,ANIMATED|LAYER,"Зарядка","Charging","Во время зарядки края экрана светятся и пульсируют энергией.","While charging, the screen edges glow and pulse with energy.","Яркость","Glow",.6f,"Ширина","Width",.45f,"Показывать уровень","Show level"),
        new Effect("glass",2,TOUCH,"Жидкое стекло","Liquid Glass","Тонкое стекло преломляет фото; касание пускает рябь.","Subtle glass refraction; touch to ripple.","Матовость","Frost",.45f,"Преломление","Refraction",.5f,null,null),
        new Effect("caustic",2,ANIMATED,"Под водой","Submerged","Фото на дне бассейна: солнечная каустика, преломление и лучи, которые следуют за наклоном.","Your photo on the floor of a sunlit pool: caustics, refraction and light shafts that follow your tilt.","Каустика","Caustics",.6f,"Глубина воды","Water depth",.45f,"Рябь","Ripples"),
        new Effect("pond",2,ANIMATED,"Пруд","Zen Pond","Тихая вода над фото: круги от капель, лёгкий ветер и солнечные блики.","Still water over your photo: rings from falling drops, a light breeze and sun glints.","Капли","Drops",.6f,"Преломление","Refraction",.5f,null,null),
        new Effect("mercury",2,ANIMATED_SWITCH,"Ртуть","Liquid Mercury","Светлые участки превращаются в жидкий хром.","Highlights turn into flowing chrome.","Рябь","Ripples",.7f,"Хром","Chrome",.55f,"Течение","Flow"),
        new Effect("fluted",2,0,"Рифлёное стекло","Fluted Glass","Архитектурное стекло с рёбрами.","Architectural reeded glass.","Частота рёбер","Rib density",.65f,"Преломление","Refraction",.55f,null,null),
        new Effect("ice",2,0,"Иней","Frosted Ice","Морозные узоры нарастают от краёв.","Frost crystals grow in from the edges.","Иней","Frost",.7f,"Преломление","Refraction",.55f,null,null),
        new Effect("condensation",2,TOUCH|WIPE,"Запотевшее стекло","Condensation","Проведи пальцем, чтобы протереть стекло. Оно снова запотеет.","Swipe to wipe the fogged glass; it fogs up again.","Матовость","Fog",.75f,"Капли","Droplets",.5f,null,null),
        new Effect("liquid",2,ANIMATED,"Жидкость","Liquid Level","Прозрачная вода, как в стакане: держит горизонт, увеличивает фото и плещется при наклоне.","Clear water, as in a glass: it stays level, magnifies the photo and sloshes as you tilt.","Уровень","Fill level",.45f,"Волны","Waves",.5f,"Пузырьки","Bubbles"),
        new Effect("droplets",2,LAYER,"Капли","Droplets","Капли на запотевшем стекле стекают по наклону, сливаются и оставляют чистые дорожки.","Raindrops on fogged glass run with your tilt, merge and leave clear trails.","Капли","Drops",.65f,"Запотевание","Fog",.5f,null,null).sim(Droplets::new),
        new Effect("depth",3,DEPTH,"Глубина","Depth Parallax","Объект отделяется от фона и двигается отдельно.","The subject separates from the background and moves on its own.","Сила","Strength",.5f,"Глубина","Depth",.6f,null,null),
        new Effect("portrait",3,DEPTH,"Портрет","Portrait Bokeh","Объект в резкости, фон размыт, как у светосильного объектива: круглые блики и «кошачий глаз» по краям.","The subject stays sharp while the background melts like a fast lens: round bokeh balls, cat’s-eye at the edges.","Диафрагма","Aperture",.6f,"Фокус","Focus depth",.3f,"Фокус от наклона","Tilt racks focus"),
        new Effect("shadow",3,DEPTH,"Парящий объект","Drop Shadow","Объект парит над фоном и отбрасывает тень.","The subject floats and casts a shadow.","Сдвиг","Lift",.65f,"Тень","Shadow",.5f,null,null),
        new Effect("pom",3,DEPTH,"Рельеф","Parallax Relief","Фото превращается в объёмный рельеф с тенями.","A ray-marched relief with self-shadowing.","Высота","Height",.7f,"Тени","Shadows",.6f,"Самозатенение","Self-shadowing"),
        new Effect("lenticular",3,DEPTH,"Стереооткрытка","Lenticular 3D","Как переливающаяся 3D-открытка с линзовым растром.","Like a lenticular 3D postcard.","Глубина","Depth",.65f,"Шаг линз","Lens pitch",.6f,"Блики линз","Lens glints"),
        new Effect("vertigo",3,DEPTH,"Вертиго","Dolly Zoom","Эффект Хичкока: фон уезжает, объект остаётся.","Hitchcock's dolly zoom: the background recedes, the subject stays.","Сила","Strength",.75f,"Хроматизм","Chromatic",.6f,"Цветная кайма","Color fringe"),
        new Effect("window",3,0,"Окно","Window","Фото — вид из окна: белая рама с переплётом, при наклоне видны её торцы, а пейзаж уходит вглубь.","Your photo is the view through a painted window: tilt to see the frame’s depth and the view behind the glass.","Глубина вида","View depth",.55f,"Рама","Frame width",.4f,"Переплёт","Muntins"),
        new Effect("sand",3,TOUCH,"Песок","Sand","Фото рассыпается песком: наклони, и он пересыпается; коснись, чтобы вырыть ямку.","Your photo crumbles into sand: tilt to pour it, touch to dig.","Количество","Amount",.6f,"Зернистость","Grain",.45f,"Песок из фото","Photo-coloured sand").sim(Sand::new),
        new Effect("golden",4,LAYER,"Золотой час","Golden Hour","Тёплый закатный свет и солнечный блик.","Warm sunset grade with a sun flare.","Сила","Strength",.75f,"Длина блика","Flare length",.6f,null,null),
        new Effect("aurora",4,ANIMATED|LAYER,"Северное сияние","Aurora","Ленты сияния колышутся над фото.","Aurora curtains wave above your photo.","Яркость","Brightness",.7f,"Насыщенность","Saturation",.55f,null,null),
        new Effect("nebula",4,ANIMATED|DEPTH,"Туманность","Star Nebula","Космическая пыль и мерцающие звёзды за объектом.","Cosmic dust and twinkling stars behind the subject.","Туманность","Nebula",.75f,"Звёзды","Stars",.65f,null,null),
        new Effect("snowglobe",4,TOUCH|LAYER,"Снежный шар","Snow Globe","Встряхни телефон: снег и блёстки закружатся и осядут по настоящей гравитации.","Shake your phone: snow and glitter swirl, then settle with real gravity.","Снег","Amount",.7f,"Блёстки","Glitter",.5f,"Цветные блёстки","Color glitter").sim(SnowGlobe::new),
        new Effect("daytime",4,LAYER,"Время суток","Time of Day","Фото живёт по часам: рассвет, полдень, золотой закат и синяя ночь.","Your photo follows the clock: dawn, noon, golden sunset and blue night.","Сила","Strength",.7f,"Ночь","Night darkness",.5f,"Звёзды ночью","Night stars"),
        new Effect("sky",4,ANIMATED|DEPTH,"Живое небо","Living Sky","Нейросеть находит на фото небо и оживляет его: плывущие облака, закат с солнцем или звёздная ночь. Остальное фото подсвечивается в тон.","The depth network finds the sky in your photo and brings it to life: drifting clouds, a sunset or a starry night, with the rest of the photo lit to match.","Облака","Clouds",.55f,"День ↔ ночь","Day ↔ night",.5f,"По часам","Follow the clock"),
        new Effect("film",5,LAYER,"Плёнка","Retro Film","Ореол, анаморфный блик и зерно киноплёнки.","Halation, an anamorphic streak and film grain.","Сила","Strength",.65f,"Тон блика","Flare tone",.5f,"Блик от наклона","Tilt flare"),
        new Effect("vintage",5,ANIMATED|LAYER,"Винтажный объектив","Vintage Lens","Тёплые цвета 70-х, искажения линзы и виньетка.","70s colour, lens distortion and vignette.","Сила","Strength",.55f,"Зерно и блик","Grain & flare",.6f,null,null),
    };

    /** Ids saved by earlier builds that now live under another id. */
    static final Map<String,String> RENAMED=Map.of("foil","holo","sheen","holo","diamond","bokeh");

    static Effect get(String id){Effect e=find(id);return e==null?ALL[0]:e;}
    /** The effect with this id (or its renamed successor), or null. */
    static Effect find(String id){
        if(id!=null){id=RENAMED.getOrDefault(id,id);for(Effect e:ALL)if(e.id.equals(id))return e;}
        return null;
    }

    /** Case-, ё- and language-insensitive search over names, descriptions and categories. */
    static List<Effect> search(String query){
        String q=normalize(query);List<Effect> out=new ArrayList<>();
        for(Effect e:ALL)if(q.isEmpty()||normalize(e.ru+" "+e.en+" "+e.aboutRu+" "+e.aboutEn+" "+CATEGORY_RU[e.category]+" "+CATEGORY_EN[e.category]+" "+tags(e)).contains(q))out.add(e);
        return out;
    }
    /** Whether top can be drawn as the second layer over base: a different LAYER effect, and at most one of the
     *  two runs a simulation (there is one physics field). */
    static boolean layerable(Effect base,Effect top){return top!=null&&top!=base&&top.has(LAYER)&&!(base.simulation!=null&&top.simulation!=null);}
    static String tags(Effect e){return (e.has(DEPTH)?"объём 3d depth ":"")+(e.has(TOUCH)?"касание touch ":"")+(e.animated(true)?"анимация animated ":"");}
    static String normalize(String s){return s==null?"":s.trim().toLowerCase(Locale.ROOT).replace('ё','е');}
    private Effects(){}
}
